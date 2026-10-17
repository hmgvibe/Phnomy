package com.ray.phnomy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.AppSettings
import com.ray.phnomy.core.BlockEvent
import com.ray.phnomy.core.FocusHistory
import com.ray.phnomy.core.FocusMode
import com.ray.phnomy.core.FocusRepository
import com.ray.phnomy.core.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

private val Context.focusSettings by preferencesDataStore(name = "focus_settings")

/** Local storage is the source of truth; all public mutations finish before publishing snapshots. */
class LocalFocusRepository internal constructor(
    private val database: FocusDatabase,
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : FocusRepository {
    constructor(context: Context, scope: CoroutineScope) : this(
        Room.databaseBuilder(context.applicationContext, FocusDatabase::class.java, "phnomy.db").build(),
        context.applicationContext.focusSettings,
        scope,
    )

    private val dao = database.dao()
    private val writeMutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val mutableSettings = MutableStateFlow(AppSettings())
    private val mutableActive = MutableStateFlow<ActiveSession?>(null)
    private val mutableHistory = MutableStateFlow<List<FocusHistory>>(emptyList())
    private val mutableBlocks = MutableStateFlow<List<BlockEvent>>(emptyList())

    override val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()
    override val active: StateFlow<ActiveSession?> = mutableActive.asStateFlow()
    override val history: StateFlow<List<FocusHistory>> = mutableHistory.asStateFlow()
    override val blocks: StateFlow<List<BlockEvent>> = mutableBlocks.asStateFlow()

    init {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // DataStore's first actual disk value must precede awaitReady, not its UI default.
                    val preferences = dataStore.data.first()
                    val rawSettings = preferences[SETTINGS_JSON]
                    val initialSettings = if (rawSettings == null) AppSettings() else {
                        runCatching { SessionJson.decodeSettings(rawSettings) }.getOrElse {
                            dataStore.edit { target ->
                                target[RECOVERED_SETTINGS_JSON] = rawSettings
                                target[SETTINGS_JSON] = SessionJson.encodeSettings(AppSettings())
                            }
                            AppSettings()
                        }
                    }
                    val initialActive = database.withTransaction {
                        val row = dao.active() ?: return@withTransaction null
                        runCatching<ActiveSession?> { row.decodedSnapshot() }.getOrElse {
                            // Preserve raw evidence and the last known progress, never fabricate completion.
                            dao.quarantine(DamagedSessionRow(row.sessionId, row.payload, System.currentTimeMillis()))
                            dao.insertHistory(row.interruptedRecovery().toRow())
                            dao.deleteActive()
                            null
                        }
                    }
                    mutableSettings.value = initialSettings
                    mutableActive.value = initialActive
                    mutableHistory.value = dao.history().map(FocusHistoryRow::toModel)
                    mutableBlocks.value = dao.blocks().map(BlockEventRow::toModel)
                }
                ready.complete(Unit)
            } catch (error: Throwable) {
                ready.completeExceptionally(error)
            }
        }
    }

    override suspend fun awaitReady() = ready.await()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        awaitReady()
        writeMutex.withLock {
            val updated = transform(mutableSettings.value).normalized()
            withContext(NonCancellable + Dispatchers.IO) {
                dataStore.edit { it[SETTINGS_JSON] = SessionJson.encodeSettings(updated) }
                mutableSettings.value = updated
            }
        }
    }

    override suspend fun saveActive(session: ActiveSession?) {
        awaitReady()
        val snapshot = session?.copy(plan = session.plan.copy(allowedPackages = session.plan.allowedPackages.toSet()))
        writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                database.withTransaction {
                    if (snapshot == null) dao.deleteActive() else {
                        SessionJson.validateActive(snapshot)
                        dao.active()?.let { existing ->
                            require(existing.sessionId == snapshot.id) { "已有進行中的專注，無法覆蓋。" }
                            val previous = existing.decodedSnapshot()
                            require(previous.plan == snapshot.plan && previous.startedAtMillis == snapshot.startedAtMillis &&
                                previous.startedAtElapsedMillis == snapshot.startedAtElapsedMillis && previous.bootCount == snapshot.bootCount) {
                                "已開始的專注計畫不可變更。"
                            }
                        }
                        dao.writeActive(snapshot.toRow())
                    }
                }
                mutableActive.value = snapshot
            }
        }
    }

    override suspend fun finalizeActive(sessionId: String, result: FocusHistory): Boolean {
        awaitReady()
        require(result.id == sessionId) { "專注紀錄識別碼不一致。" }
        return writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                val updatedHistory = database.withTransaction {
                    val current = dao.active()
                    if (current?.sessionId != sessionId) return@withTransaction null
                    dao.insertHistory(result.toRow())
                    dao.deleteActive()
                    dao.history().map(FocusHistoryRow::toModel)
                }
                if (updatedHistory != null) {
                    mutableHistory.value = updatedHistory
                    mutableActive.value = null
                }
                updatedHistory != null
            }
        }
    }

    override suspend fun recordBlock(event: BlockEvent) {
        awaitReady()
        writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                // Stale accessibility callbacks after completion must not create orphan events.
                val updatedBlocks = database.withTransaction {
                    if (dao.active()?.sessionId == event.sessionId) dao.insertBlock(event.toRow())
                    dao.blocks().map(BlockEventRow::toModel)
                }
                mutableBlocks.value = updatedBlocks
            }
        }
    }

    override suspend fun clearHistory() {
        awaitReady()
        writeMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                database.withTransaction {
                    check(dao.active() == null) { "專注進行中，無法清除紀錄。" }
                    dao.deleteHistory()
                    dao.deleteBlocks()
                    dao.deleteDamagedSessions()
                }
                mutableHistory.value = emptyList()
                mutableBlocks.value = emptyList()
            }
        }
    }

    private fun ActiveSessionRow.interruptedRecovery(): FocusHistory {
        val planned = plannedFocusMillis.coerceAtLeast(0)
        val focused = checkpointFocusedMillis.coerceIn(0, planned)
        val safeStart = startedAtMillis.coerceAtLeast(0)
        return FocusHistory(
            id = sessionId.ifBlank { "recovered-${UUID.randomUUID()}" },
            mode = enumValues<FocusMode>().firstOrNull { it.name == mode } ?: FocusMode.SINGLE,
            startedAtMillis = safeStart,
            endedAtMillis = System.currentTimeMillis().coerceAtLeast(safeStart),
            focusedMillis = focused,
            plannedFocusMillis = planned,
            completedRounds = (focused / (focusMinutes.coerceAtLeast(1) * 60_000L)).toInt().coerceIn(0, rounds.coerceAtLeast(0)),
            outcome = SessionOutcome.INTERRUPTED,
            interruptionReason = "本機專注紀錄損壞，已保留可恢復的進度。",
        )
    }

    private companion object {
        val SETTINGS_JSON = stringPreferencesKey("settings_json_v1")
        val RECOVERED_SETTINGS_JSON = stringPreferencesKey("recovered_settings_json")
    }
}
