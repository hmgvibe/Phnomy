package com.ray.phnomy.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.BlockEvent
import com.ray.phnomy.core.FocusHistory
import com.ray.phnomy.core.FocusMode
import com.ray.phnomy.core.SessionOutcome

@Entity(tableName = "active_session")
internal data class ActiveSessionRow(
    @PrimaryKey val slot: Int = 1,
    val sessionId: String,
    val payload: String,
    // Independent recovery metadata allows a malformed JSON snapshot to be interrupted safely.
    val mode: String,
    val startedAtMillis: Long,
    val checkpointFocusedMillis: Long,
    val plannedFocusMillis: Long,
    val focusMinutes: Int,
    val rounds: Int,
)

@Entity(tableName = "focus_history", indices = [Index("startedAtMillis")])
internal data class FocusHistoryRow(
    @PrimaryKey val id: String,
    val mode: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val focusedMillis: Long,
    val plannedFocusMillis: Long,
    val completedRounds: Int,
    val outcome: String,
    val exitReason: String,
    val interruptionReason: String,
)

@Entity(tableName = "block_event", indices = [Index("sessionId"), Index("atMillis")])
internal data class BlockEventRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val packageName: String,
    val atMillis: Long,
)

@Entity(tableName = "damaged_session")
internal data class DamagedSessionRow(@PrimaryKey val sessionId: String, val payload: String, val recoveredAtMillis: Long)

@Dao
internal interface FocusDao {
    @Query("SELECT * FROM active_session WHERE slot = 1")
    suspend fun active(): ActiveSessionRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeActive(row: ActiveSessionRow)

    @Query("DELETE FROM active_session WHERE slot = 1")
    suspend fun deleteActive()

    @Query("SELECT * FROM focus_history ORDER BY startedAtMillis DESC, id DESC")
    suspend fun history(): List<FocusHistoryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(row: FocusHistoryRow): Long

    @Query("DELETE FROM focus_history")
    suspend fun deleteHistory()

    @Query("SELECT * FROM block_event ORDER BY atMillis DESC, id DESC")
    suspend fun blocks(): List<BlockEventRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlock(row: BlockEventRow)

    @Query("DELETE FROM block_event")
    suspend fun deleteBlocks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun quarantine(row: DamagedSessionRow)

    @Query("DELETE FROM damaged_session")
    suspend fun deleteDamagedSessions()
}

@Database(entities = [ActiveSessionRow::class, FocusHistoryRow::class, BlockEventRow::class, DamagedSessionRow::class], version = 1, exportSchema = true)
internal abstract class FocusDatabase : RoomDatabase() {
    abstract fun dao(): FocusDao
}

internal fun ActiveSession.toRow() = ActiveSessionRow(
    sessionId = id,
    payload = SessionJson.encodeActive(this),
    mode = plan.mode.name,
    startedAtMillis = startedAtMillis,
    checkpointFocusedMillis = checkpointFocusedMillis,
    plannedFocusMillis = plan.plannedFocusMillis(),
    focusMinutes = plan.focusMinutes,
    rounds = if (plan.mode == FocusMode.SINGLE) 1 else plan.rounds,
)

/** A valid JSON document can still be damaged; independent columns must describe the same snapshot. */
internal fun ActiveSessionRow.decodedSnapshot(): ActiveSession = SessionJson.decodeActive(payload).also { session ->
    require(session.id == sessionId && session.startedAtMillis == startedAtMillis &&
        session.plan.mode.name == mode && session.plan.focusMinutes == focusMinutes &&
        session.plan.plannedFocusMillis() == plannedFocusMillis &&
        session.checkpointFocusedMillis == checkpointFocusedMillis &&
        (if (session.plan.mode == FocusMode.SINGLE) 1 else session.plan.rounds) == rounds) {
        "Active session metadata does not match its snapshot"
    }
}

internal fun FocusHistory.toRow() = FocusHistoryRow(id, mode.name, startedAtMillis, endedAtMillis, focusedMillis,
    plannedFocusMillis, completedRounds, outcome.name, exitReason, interruptionReason)

internal fun FocusHistoryRow.toModel() = FocusHistory(id, enumValues<FocusMode>().firstOrNull { it.name == mode } ?: FocusMode.SINGLE,
    startedAtMillis, endedAtMillis, focusedMillis, plannedFocusMillis, completedRounds,
    enumValues<SessionOutcome>().firstOrNull { it.name == outcome } ?: SessionOutcome.INTERRUPTED, exitReason, interruptionReason)

internal fun BlockEvent.toRow() = BlockEventRow(id, sessionId, packageName, atMillis)
internal fun BlockEventRow.toModel() = BlockEvent(id, sessionId, packageName, atMillis)
