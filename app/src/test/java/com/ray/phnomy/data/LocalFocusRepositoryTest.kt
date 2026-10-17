package com.ray.phnomy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.AppSettings
import com.ray.phnomy.core.BlockEvent
import com.ray.phnomy.core.FocusHistory
import com.ray.phnomy.core.FocusMode
import com.ray.phnomy.core.FocusPlan
import com.ray.phnomy.core.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LocalFocusRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `completion is stored exactly once even when two callers race`() = runBlocking {
        fixture().use { fixture ->
            val repository = fixture.repository
            repository.awaitReady()
            repository.saveActive(session())
            val first = async(Dispatchers.IO) { repository.finalizeActive("active", history()) }
            val second = async(Dispatchers.IO) { repository.finalizeActive("active", history()) }
            assertEquals(1, listOf(first.await(), second.await()).count { it })
            assertNull(repository.active.value)
            assertEquals(listOf(history()), repository.history.value)
            assertNull(fixture.database.dao().active())
            assertEquals(1, fixture.database.dao().history().size)
        }
    }

    @Test fun `wrong completion identifier cannot clear current active session`() = runBlocking {
        fixture().use { fixture ->
            fixture.repository.awaitReady()
            fixture.repository.saveActive(session())
            assertFalse(fixture.repository.finalizeActive("different", history().copy(id = "different")))
            assertEquals("active", fixture.repository.active.value?.id)
            assertTrue(fixture.repository.history.value.isEmpty())
        }
    }

    @Test fun `clearing is rejected during active and later clears records while preserving settings`() = runBlocking {
        fixture().use { fixture ->
            val repository = fixture.repository
            repository.awaitReady()
            repository.updateSettings { it.copy(focusMinutes = 50, allowedPackages = setOf("com.keep")) }
            repository.saveActive(session())
            repository.recordBlock(BlockEvent("block", "active", "com.social", 5_000))
            try {
                repository.clearHistory()
                fail("Active session must prevent clearing")
            } catch (_: IllegalStateException) { }
            assertEquals(1, repository.blocks.value.size)
            assertEquals("active", repository.active.value?.id)
            repository.finalizeActive("active", history())
            repository.clearHistory()
            assertTrue(repository.history.value.isEmpty())
            assertTrue(repository.blocks.value.isEmpty())
            assertEquals(50, repository.settings.value.focusMinutes)
            assertEquals(setOf("com.keep"), repository.settings.value.allowedPackages)
            assertTrue(fixture.database.dao().history().isEmpty())
            assertTrue(fixture.database.dao().blocks().isEmpty())
        }
    }

    @Test fun `initialization reads existing settings and active before readiness`() = runBlocking {
        fixture(beforeOpen = { database, settings ->
            database.dao().writeActive(session().toRow())
            settings.edit { it[stringPreferencesKey("settings_json_v1")] = SessionJson.encodeSettings(AppSettings(focusMinutes = 45)) }
        }).use { fixture ->
            fixture.repository.awaitReady()
            assertEquals(45, fixture.repository.settings.value.focusMinutes)
            assertEquals(session(), fixture.repository.active.value)
        }
    }

    @Test fun `damaged active becomes interrupted with checkpoint history and never completed`() = runBlocking {
        fixture(beforeOpen = { database, _ ->
            database.dao().writeActive(session().copy(checkpointFocusedMillis = 120_000).toRow().copy(payload = "{broken"))
        }).use { fixture ->
            fixture.repository.awaitReady()
            assertNull(fixture.repository.active.value)
            val recovered = fixture.repository.history.value.single()
            assertEquals("active", recovered.id)
            assertEquals(SessionOutcome.INTERRUPTED, recovered.outcome)
            assertEquals(120_000L, recovered.focusedMillis)
            assertTrue(recovered.interruptionReason.isNotBlank())
            assertNull(fixture.database.dao().active())
        }
    }

    @Test fun `updates cannot change committed plan but can advance checkpoint`() = runBlocking {
        fixture().use { fixture ->
            val repository = fixture.repository
            repository.awaitReady()
            repository.saveActive(session())
            try {
                repository.saveActive(session().copy(plan = FocusPlan(focusMinutes = 1)))
                fail("Committed duration must stay immutable")
            } catch (_: IllegalArgumentException) { }
            repository.saveActive(session().copy(checkpointElapsedMillis = 130_000, checkpointFocusedMillis = 30_000))
            assertEquals(30_000L, repository.active.value?.checkpointFocusedMillis)
            assertEquals(25, repository.active.value?.plan?.focusMinutes)
        }
    }

    @Test fun `late block callbacks do not create records after completion`() = runBlocking {
        fixture().use { fixture ->
            val repository = fixture.repository
            repository.awaitReady()
            repository.saveActive(session())
            repository.finalizeActive("active", history())
            repository.recordBlock(BlockEvent("late", "active", "com.social", 10_000))
            assertTrue(repository.blocks.value.isEmpty())
        }
    }

    @Test fun `changing defaults during focus only affects future sessions`() = runBlocking {
        fixture().use { fixture ->
            val repository = fixture.repository
            repository.awaitReady()
            repository.saveActive(session())
            repository.updateSettings { it.copy(focusMinutes = 5, allowedPackages = setOf("com.social")) }
            assertEquals(5, repository.settings.value.focusMinutes)
            assertEquals(25, repository.active.value?.plan?.focusMinutes)
            assertTrue(repository.active.value!!.plan.allowedPackages.isEmpty())
            assertEquals(25, SessionJson.decodeActive(fixture.database.dao().active()!!.payload).plan.focusMinutes)
        }
    }

    @Test fun `damaged settings JSON is isolated while active and history remain intact`() = runBlocking {
        fixture(beforeOpen = { database, settings ->
            database.dao().writeActive(session().toRow())
            database.dao().insertHistory(history().copy(id = "older").toRow())
            settings.edit { it[stringPreferencesKey("settings_json_v1")] = "bad JSON" }
        }).use { fixture ->
            fixture.repository.awaitReady()
            assertEquals(AppSettings(), fixture.repository.settings.value)
            assertEquals(session(), fixture.repository.active.value)
            assertEquals("older", fixture.repository.history.value.single().id)
        }
    }

    @Test fun `valid JSON with mismatched session id is interrupted instead of becoming unfinishable`() = runBlocking {
        fixture(beforeOpen = { database, _ ->
            val row = session().toRow()
            database.dao().writeActive(row.copy(payload = SessionJson.encodeActive(session().copy(id = "mismatched-id"))))
        }).use { fixture ->
            fixture.repository.awaitReady()
            assertNull(fixture.repository.active.value)
            val recovered = fixture.repository.history.value.single()
            assertEquals("active", recovered.id)
            assertEquals(SessionOutcome.INTERRUPTED, recovered.outcome)
            assertNull(fixture.database.dao().active())
        }
    }

    @Test fun `valid JSON with shortened duration cannot restore as successful session`() = runBlocking {
        fixture(beforeOpen = { database, _ ->
            val row = session().toRow()
            database.dao().writeActive(row.copy(payload = SessionJson.encodeActive(session().copy(plan = FocusPlan(focusMinutes = 1)))))
        }).use { fixture ->
            fixture.repository.awaitReady()
            assertNull(fixture.repository.active.value)
            val recovered = fixture.repository.history.value.single()
            assertEquals(SessionOutcome.INTERRUPTED, recovered.outcome)
            assertEquals(25 * 60_000L, recovered.plannedFocusMillis)
            assertEquals(0L, recovered.focusedMillis)
        }
    }

    @Test fun `cancellation after settings commit still publishes the committed state`() = runBlocking {
        val committed = CompletableDeferred<Unit>()
        val publish = CompletableDeferred<Unit>()
        fixture(wrapStore = { delegate ->
            object : DataStore<Preferences> {
                override val data = delegate.data
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    val updated = delegate.updateData(transform)
                    committed.complete(Unit)
                    publish.await()
                    return updated
                }
            }
        }).use { fixture ->
            fixture.repository.awaitReady()
            val writer = launch(Dispatchers.Default) { fixture.repository.updateSettings { it.copy(focusMinutes = 50) } }
            committed.await()
            writer.cancel()
            publish.complete(Unit)
            writer.join()
            assertTrue(writer.isCancelled)
            assertEquals(50, fixture.repository.settings.value.focusMinutes)
            val stored = fixture.dataStore.data.first()[stringPreferencesKey("settings_json_v1")]!!
            assertEquals(50, SessionJson.decodeSettings(stored).focusMinutes)
        }
    }

    @Test fun `cancelled writer waiting for mutex never mutates stored settings`() = runBlocking {
        val firstCommitted = CompletableDeferred<Unit>()
        val publishFirst = CompletableDeferred<Unit>()
        val writes = java.util.concurrent.atomic.AtomicInteger()
        fixture(wrapStore = { delegate ->
            object : DataStore<Preferences> {
                override val data = delegate.data
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    val updated = delegate.updateData(transform)
                    writes.incrementAndGet()
                    firstCommitted.complete(Unit)
                    publishFirst.await()
                    return updated
                }
            }
        }).use { fixture ->
            fixture.repository.awaitReady()
            val first = launch(Dispatchers.Default) { fixture.repository.updateSettings { it.copy(focusMinutes = 50) } }
            firstCommitted.await()
            val secondStarted = CompletableDeferred<Unit>()
            val second = launch(Dispatchers.Default) {
                secondStarted.complete(Unit)
                fixture.repository.updateSettings { it.copy(focusMinutes = 1) }
            }
            secondStarted.await()
            second.cancelAndJoin()
            publishFirst.complete(Unit)
            first.join()
            assertEquals(1, writes.get())
            assertEquals(50, fixture.repository.settings.value.focusMinutes)
        }
    }

    private suspend fun fixture(
        beforeOpen: suspend (FocusDatabase, DataStore<Preferences>) -> Unit = { _, _ -> },
        wrapStore: ((DataStore<Preferences>) -> DataStore<Preferences>)? = null,
    ): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = temporary.root.resolve("${java.util.UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        beforeOpen(database, dataStore)
        return Fixture(database, scope, LocalFocusRepository(database, wrapStore?.invoke(dataStore) ?: dataStore, scope), dataStore)
    }

    private data class Fixture(val database: FocusDatabase, val scope: CoroutineScope, val repository: LocalFocusRepository, val dataStore: DataStore<Preferences>) : AutoCloseable {
        override fun close() {
            scope.cancel()
            database.close()
        }
    }

    private fun session() = ActiveSession("active", FocusPlan(), 1_000, 100_000, 2)
    private fun history() = FocusHistory("active", FocusMode.SINGLE, 1_000, 1_501_000, 1_500_000, 1_500_000, 1, SessionOutcome.COMPLETED)
}
