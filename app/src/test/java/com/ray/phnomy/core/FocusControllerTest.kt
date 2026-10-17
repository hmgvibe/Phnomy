package com.ray.phnomy.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusControllerTest {
    @Test fun `start waits until settings and active storage are actually ready`() = runBlocking {
        fixture(ready = false).use { f ->
            val request = async { f.controller.start(FocusPlan()) }
            kotlinx.coroutines.yield()
            assertNull(f.repository.active.value)
            f.repository.settings.value = AppSettings(accessibilityConsent = true)
            f.repository.ready.complete(Unit)
            assertTrue(request.await().isSuccess)
            assertNotNull(f.repository.active.value)
        }
    }

    @Test fun `start requires disclosure consent and valid timing`() = runBlocking {
        fixture().use { f ->
            f.repository.settings.value = AppSettings(accessibilityConsent = false)
            assertTrue(f.controller.start(FocusPlan()).isFailure)
            f.repository.settings.value = AppSettings(accessibilityConsent = true)
            assertTrue(f.controller.start(FocusPlan(focusMinutes = 0)).isFailure)
            assertNull(f.repository.active.value)
            assertTrue(f.effects.scheduled.isEmpty())
        }
    }

    @Test fun `platform permission problem blocks starting before persistence`() = runBlocking {
        fixture().use { f ->
            f.effects.problem = "請開啟準確鬧鐘"
            val result = f.controller.start(FocusPlan())
            assertEquals("請開啟準確鬧鐘", result.exceptionOrNull()?.message)
            assertNull(f.repository.active.value)
        }
    }

    @Test fun `start takes independent whitelist snapshot and rejects a second session`() = runBlocking {
        fixture().use { f ->
            val packages = mutableSetOf("com.allowed")
            assertTrue(f.controller.start(FocusPlan(allowedPackages = packages)).isSuccess)
            val id = f.repository.active.value!!.id
            packages.add("com.distraction")
            assertEquals(setOf("com.allowed"), f.repository.active.value!!.plan.allowedPackages)
            assertTrue(f.controller.start(FocusPlan(focusMinutes = 50)).isFailure)
            assertEquals(id, f.repository.active.value!!.id)
            assertEquals(25, f.repository.active.value!!.plan.focusMinutes)
        }
    }

    @Test fun `elapsed clock checkpoints every fifteen seconds and ignores wall clock changes`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            val original = f.repository.active.value!!
            f.clock.advance(14_999)
            f.clock.wall = -1_000_000
            f.controller.reconcile()
            assertEquals(original.checkpointElapsedMillis, f.repository.active.value!!.checkpointElapsedMillis)
            f.clock.advance(1)
            f.controller.reconcile()
            assertEquals(original.startedAtElapsedMillis + 15_000, f.repository.active.value!!.checkpointElapsedMillis)
            assertEquals(15_000L, f.repository.active.value!!.checkpointFocusedMillis)
            assertEquals(25 * 60_000L - 15_000, f.controller.progress.value!!.remainingMillis)
        }
    }

    @Test fun `phase transitions keep same plan and protection through final long rest`() = runBlocking {
        fixture().use { f ->
            val plan = FocusPlan(FocusMode.POMODORO, focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 2, rounds = 2, longBreakEvery = 2)
            f.controller.start(plan)
            f.clock.advance(60_000)
            f.controller.reconcile()
            assertEquals(SessionPhase.SHORT_BREAK, f.controller.progress.value?.phase)
            assertEquals(plan, f.repository.active.value?.plan)
            f.clock.advance(120_000)
            f.controller.reconcile()
            assertEquals(SessionPhase.LONG_BREAK, f.controller.progress.value?.phase)
            assertNotNull(f.repository.active.value)
            f.clock.advance(120_000)
            f.controller.reconcile()
            assertNull(f.repository.active.value)
            assertEquals(120_000L, f.repository.history.value.single().focusedMillis)
            assertEquals(SessionOutcome.COMPLETED, f.repository.history.value.single().outcome)
        }
    }

    @Test fun `exit requires a trimmed nonblank reason no longer than one hundred chars`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            assertTrue(f.controller.requestExit(" \n ").isFailure)
            assertTrue(f.controller.requestExit("字".repeat(101)).isFailure)
            assertNull(f.repository.active.value!!.exitRequestedAtElapsedMillis)
            assertTrue(f.controller.requestExit("  接緊急電話  ").isSuccess)
            assertEquals("接緊急電話", f.repository.active.value!!.exitReason)
        }
    }

    @Test fun `early confirmation fails until full sixty seconds then stores an exited result`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            f.clock.advance(10_000)
            f.controller.requestExit("接電話")
            f.clock.advance(59_999)
            assertTrue(f.controller.confirmExit().isFailure)
            assertNotNull(f.repository.active.value)
            assertTrue(f.repository.history.value.isEmpty())
            f.clock.advance(1)
            assertTrue(f.controller.confirmExit().isSuccess)
            val result = f.repository.history.value.single()
            assertEquals(SessionOutcome.EXITED, result.outcome)
            assertEquals("接電話", result.exitReason)
            assertEquals(70_000L, result.focusedMillis)
            assertEquals(listOf(SessionOutcome.EXITED), f.effects.ended)
        }
    }

    @Test fun `repeating same reason preserves countdown while changing reason restarts it`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            f.controller.requestExit("急事")
            val first = f.repository.active.value!!.exitRequestedAtElapsedMillis
            f.clock.advance(30_000)
            f.controller.requestExit(" 急事 ")
            assertEquals(first, f.repository.active.value!!.exitRequestedAtElapsedMillis)
            f.controller.requestExit("另一件急事")
            assertEquals(f.clock.elapsed, f.repository.active.value!!.exitRequestedAtElapsedMillis)
            f.clock.advance(30_000)
            assertTrue(f.controller.confirmExit().isFailure)
        }
    }

    @Test fun `cancel exit preserves original deadline and clears confirmation authorization`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            val deadline = f.controller.progress.value!!.phaseDeadlineElapsedMillis
            f.controller.requestExit("取消測試")
            f.clock.advance(30_000)
            f.controller.cancelExit()
            assertEquals("", f.repository.active.value!!.exitReason)
            assertNull(f.repository.active.value!!.exitRequestedAtElapsedMillis)
            assertEquals(deadline, f.controller.progress.value!!.phaseDeadlineElapsedMillis)
            f.clock.advance(60_000)
            assertTrue(f.controller.confirmExit().isFailure)
            assertNotNull(f.repository.active.value)
        }
    }

    @Test fun `natural completion wins when exit wait extends beyond session deadline`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(focusMinutes = 1))
            f.clock.advance(10_000)
            f.controller.requestExit("急事")
            f.clock.advance(50_000)
            assertTrue(f.controller.confirmExit().isSuccess)
            val history = f.repository.history.value.single()
            assertEquals(SessionOutcome.COMPLETED, history.outcome)
            assertEquals("", history.exitReason)
            assertEquals(60_000L, history.focusedMillis)
            assertEquals(1, f.repository.finalizedCount)
        }
    }

    @Test fun `repeated reconciliation and confirmation finalize one history row only`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(focusMinutes = 1))
            f.clock.advance(60_000)
            val calls = List(8) { async(Dispatchers.Default) { f.controller.reconcile() } }
            calls.forEach { it.await() }
            f.controller.confirmExit()
            f.controller.interrupt("late signal")
            assertEquals(1, f.repository.finalizedCount)
            assertEquals(1, f.repository.history.value.size)
            assertEquals(listOf(SessionOutcome.COMPLETED), f.effects.ended)
        }
    }

    @Test fun `reboot restores interrupted checkpoint rather than fake success`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            f.clock.advance(30_000)
            f.controller.reconcile()
            f.clock.boot++
            f.clock.elapsed = 5_000
            f.clock.wall += 900_000
            f.controller.reconcile()
            val history = f.repository.history.value.single()
            assertEquals(SessionOutcome.INTERRUPTED, history.outcome)
            assertEquals(30_000L, history.focusedMillis)
            assertEquals(1_000_000L + 30_000, history.endedAtMillis)
            assertNull(f.repository.active.value)
        }
    }

    @Test fun `protection loss interrupts using last persisted checkpoint`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan())
            f.clock.advance(30_000)
            f.controller.reconcile()
            f.protectionActive = false
            f.clock.advance(300_000)
            f.controller.reconcile()
            val history = f.repository.history.value.single()
            assertEquals(SessionOutcome.INTERRUPTED, history.outcome)
            assertEquals(30_000L, history.focusedMillis)
            assertTrue(history.interruptionReason.isNotBlank())
            assertNull(f.repository.active.value)
            assertEquals(false, f.effects.quietMode.last())
        }
    }

    @Test fun `explicit service interruption preserves checkpoint and clears quiet rule`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(quietMode = true))
            f.clock.advance(15_000)
            f.controller.reconcile()
            f.clock.advance(12_000)
            f.controller.interrupt("無障礙服務已停用")
            assertEquals(SessionOutcome.INTERRUPTED, f.repository.history.value.single().outcome)
            assertEquals(15_000L, f.repository.history.value.single().focusedMillis)
            assertEquals(listOf(true, false), f.effects.quietMode)
        }
    }

    @Test fun `whitelist apps are exempt and blocked callbacks after completion are ignored`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(focusMinutes = 1, allowedPackages = setOf("com.allowed")))
            f.controller.recordBlock("com.allowed")
            assertTrue(f.repository.blocks.value.isEmpty())
            f.controller.recordBlock("com.social")
            assertEquals("com.social", f.repository.blocks.value.single().packageName)
            f.clock.advance(60_000)
            f.controller.recordBlock("com.social")
            assertEquals(1, f.repository.blocks.value.size)
            assertEquals(SessionOutcome.COMPLETED, f.repository.history.value.single().outcome)
        }
    }

    @Test fun `unchanged phase avoids repeated alarms and applies quiet mode once`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(quietMode = true))
            repeat(5) { f.clock.advance(1_000); f.controller.reconcile() }
            assertEquals(1, f.effects.scheduled.size)
            assertEquals(listOf(true), f.effects.quietMode)
            f.controller.requestExit("測試")
            assertEquals(2, f.effects.scheduled.size)
            f.controller.cancelExit()
            assertEquals(3, f.effects.scheduled.size)
        }
    }

    @Test fun `idle restoration clears stale system effects without inventing history`() = runBlocking {
        fixture().use { f ->
            f.controller.reconcile()
            f.controller.reconcile()
            assertEquals(listOf(false, false), f.effects.quietMode)
            assertEquals(listOf(SessionOutcome.INTERRUPTED), f.effects.ended)
            assertTrue(f.repository.history.value.isEmpty())
            assertEquals(0, f.repository.finalizedCount)
            assertNull(f.controller.progress.value)
        }
    }

    @Test fun `failed idle cleanup is retried on next reconciliation`() = runBlocking {
        fixture().use { f ->
            f.effects.failEndCalls = 1
            f.controller.reconcile()
            assertTrue(f.effects.ended.isEmpty())
            f.controller.reconcile()
            f.controller.reconcile()
            assertEquals(listOf(SessionOutcome.INTERRUPTED), f.effects.ended)
            assertEquals(0, f.repository.finalizedCount)
            assertTrue(f.repository.history.value.isEmpty())
        }
    }

    @Test fun `starting after idle cleanup resets cleanup state for a later real completion`() = runBlocking {
        fixture().use { f ->
            f.controller.reconcile()
            assertTrue(f.controller.start(FocusPlan(focusMinutes = 1, quietMode = true)).isSuccess)
            f.clock.advance(60_000)
            f.controller.reconcile()
            f.controller.reconcile()
            assertEquals(listOf(SessionOutcome.INTERRUPTED, SessionOutcome.COMPLETED), f.effects.ended)
            assertEquals(1, f.repository.finalizedCount)
            assertEquals(SessionOutcome.COMPLETED, f.repository.history.value.single().outcome)
            assertEquals(false, f.effects.quietMode.last())
        }
    }

    @Test fun `late UI subscriber receives result even when it never observed active state`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(focusMinutes = 1))
            f.clock.advance(60_000)
            f.controller.reconcile()
            // The UI may be created only after an Application-level auto tick completes.
            val result = f.controller.latestResult.first()
            assertNotNull(result)
            assertEquals(f.repository.history.value.single(), result)
            assertEquals(SessionOutcome.COMPLETED, result!!.outcome)
            f.controller.reconcile()
            assertEquals(result, f.controller.latestResult.value)
        }
    }

    @Test fun `result dismissal requires matching id and new session clears prior result`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(focusMinutes = 1))
            f.clock.advance(60_000)
            f.controller.reconcile()
            val first = f.controller.latestResult.value!!
            f.controller.dismissLatestResult("wrong-id")
            f.controller.dismissLatestResult(null)
            assertEquals(first, f.controller.latestResult.value)
            assertTrue(f.controller.start(FocusPlan(focusMinutes = 1)).isSuccess)
            assertNull(f.controller.latestResult.value)
            f.clock.advance(60_000)
            f.controller.reconcile()
            val second = f.controller.latestResult.value!!
            assertTrue(first.id != second.id)
            f.controller.dismissLatestResult(first.id)
            assertEquals(second, f.controller.latestResult.value)
            f.controller.dismissLatestResult(second.id)
            assertNull(f.controller.latestResult.value)
            assertEquals(2, f.repository.history.value.size)
        }
    }

    @Test fun `restored pending exit keeps original sixty second deadline after process recreation`() = runBlocking {
        fixture().use { f ->
            val persisted = ActiveSession("restored", FocusPlan(), 1_000_000, 100_000, 3,
                exitReason = "急事", exitRequestedAtElapsedMillis = 110_000,
                checkpointElapsedMillis = 120_000, checkpointFocusedMillis = 20_000)
            f.repository.active.value = persisted
            f.clock.advance(50_000)
            f.controller.reconcile()
            assertEquals(20_000L, f.controller.progress.value?.exitRemainingMillis)
            assertTrue(f.controller.confirmExit().isFailure)
            f.clock.advance(20_000)
            assertTrue(f.controller.confirmExit().isSuccess)
            assertEquals(SessionOutcome.EXITED, f.repository.history.value.single().outcome)
            assertEquals("急事", f.repository.history.value.single().exitReason)
            assertEquals(70_000L, f.repository.history.value.single().focusedMillis)
        }
    }

    @Test fun `caller cancellation after finalization still publishes result and cleans system effects`() = runBlocking {
        fixture().use { f ->
            f.controller.start(FocusPlan(focusMinutes = 1, quietMode = true))
            f.clock.advance(60_000)
            val committed = CompletableDeferred<Unit>()
            val returnFromCommit = CompletableDeferred<Unit>()
            f.repository.afterFinalize = {
                committed.complete(Unit)
                returnFromCommit.await()
            }
            val finisher = launch(Dispatchers.Default) { f.controller.reconcile() }
            committed.await()
            finisher.cancel()
            returnFromCommit.complete(Unit)
            finisher.join()
            assertTrue(finisher.isCancelled)
            assertEquals(SessionOutcome.COMPLETED, f.controller.latestResult.value?.outcome)
            assertEquals(listOf(SessionOutcome.COMPLETED), f.effects.ended)
            assertEquals(false, f.effects.quietMode.last())
            assertEquals(1, f.repository.finalizedCount)
        }
    }

    @Test fun `restored expired session waits for shared protection gate before interrupted checkpoint`() = runBlocking {
        val enteredGate = CompletableDeferred<Unit>()
        val allowRestore = CompletableDeferred<Unit>()
        val gateCalls = java.util.concurrent.atomic.AtomicInteger()
        fixture(restoredGate = {
            gateCalls.incrementAndGet()
            enteredGate.complete(Unit)
            allowRestore.await()
        }).use { f ->
            f.repository.active.value = ActiveSession("restored", FocusPlan(focusMinutes = 1), 1_000_000, 100_000, 3,
                checkpointElapsedMillis = 115_000, checkpointFocusedMillis = 15_000)
            f.clock.advance(120_000)
            val requests = listOf(
                async(Dispatchers.Default) { f.controller.reconcile() },
                async(Dispatchers.Default) { f.controller.confirmExit() },
                async(Dispatchers.Default) { f.controller.recordBlock("com.social") },
            )
            enteredGate.await()
            assertNotNull(f.repository.active.value)
            assertTrue(f.repository.history.value.isEmpty())
            assertNull(f.controller.latestResult.value)
            assertEquals(0, f.repository.finalizedCount)
            assertTrue(f.effects.ended.isEmpty())
            f.protectionActive = false
            allowRestore.complete(Unit)
            requests.forEach { it.await() }
            val result = f.repository.history.value.single()
            assertEquals(SessionOutcome.INTERRUPTED, result.outcome)
            assertEquals(15_000L, result.focusedMillis)
            assertEquals(1_015_000L, result.endedAtMillis)
            assertEquals(1, f.repository.finalizedCount)
            assertTrue(f.repository.blocks.value.isEmpty())
            assertEquals(1, gateCalls.get())
            f.controller.reconcile()
            assertEquals(1, gateCalls.get())
        }
    }

    @Test fun `restored protection can reconnect before expiry is reconciled`() = runBlocking {
        val enteredGate = CompletableDeferred<Unit>()
        val allowRestore = CompletableDeferred<Unit>()
        fixture(restoredGate = { enteredGate.complete(Unit); allowRestore.await() }).use { f ->
            f.repository.active.value = ActiveSession("restored", FocusPlan(focusMinutes = 1), 1_000_000, 100_000, 3,
                checkpointElapsedMillis = 115_000, checkpointFocusedMillis = 15_000)
            f.clock.advance(120_000)
            f.protectionActive = false
            val request = async(Dispatchers.Default) { f.controller.reconcile() }
            enteredGate.await()
            assertTrue(f.repository.history.value.isEmpty())
            f.protectionActive = true
            allowRestore.complete(Unit)
            request.await()
            val result = f.repository.history.value.single()
            assertEquals(SessionOutcome.COMPLETED, result.outcome)
            assertEquals(60_000L, result.focusedMillis)
            assertEquals(1_060_000L, result.endedAtMillis)
        }
    }

    private fun fixture(ready: Boolean = true, restoredGate: suspend () -> Unit = {}): Fixture {
        val repository = FakeRepository(ready)
        val clock = FakeClock()
        val effects = FakeEffects()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return Fixture(repository, clock, effects, scope, restoredGate)
    }

    private class Fixture(val repository: FakeRepository, val clock: FakeClock, val effects: FakeEffects, val scope: CoroutineScope,
        restoredGate: suspend () -> Unit) : AutoCloseable {
        var protectionActive = true
        val controller = FocusController(repository, clock, effects, scope, protectionEnabled = { protectionActive }, autoTick = false,
            awaitRestoredProtection = restoredGate)
        override fun close() = scope.cancel()
    }

    private class FakeClock : FocusClock {
        var wall = 1_000_000L
        var elapsed = 100_000L
        var boot = 3
        override fun read() = ClockReading(wall, elapsed, boot)
        fun advance(delta: Long) { wall += delta; elapsed += delta }
    }

    private class FakeRepository(isReady: Boolean) : FocusRepository {
        val ready = CompletableDeferred<Unit>().apply { if (isReady) complete(Unit) }
        override val settings = MutableStateFlow(AppSettings(accessibilityConsent = isReady))
        override val active = MutableStateFlow<ActiveSession?>(null)
        override val history = MutableStateFlow<List<FocusHistory>>(emptyList())
        override val blocks = MutableStateFlow<List<BlockEvent>>(emptyList())
        var finalizedCount = 0
        var afterFinalize: (suspend () -> Unit)? = null
        override suspend fun awaitReady() = ready.await()
        override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
        override suspend fun saveActive(session: ActiveSession?) { active.value = session }
        override suspend fun finalizeActive(sessionId: String, result: FocusHistory): Boolean {
            if (active.value?.id != sessionId) return false
            history.value += result
            active.value = null
            finalizedCount++
            afterFinalize?.invoke()
            return true
        }
        override suspend fun recordBlock(event: BlockEvent) { blocks.value += event }
        override suspend fun clearHistory() { check(active.value == null); history.value = emptyList(); blocks.value = emptyList() }
    }

    private class FakeEffects : SessionEffects {
        var problem: String? = null
        var failEndCalls = 0
        val scheduled = mutableListOf<SessionProgress>()
        val quietMode = mutableListOf<Boolean>()
        val ended = mutableListOf<SessionOutcome>()
        override fun startProblem() = problem
        override fun schedule(session: ActiveSession, progress: SessionProgress) { scheduled += progress }
        override fun activateQuietMode(enabled: Boolean) { quietMode += enabled }
        override fun showProgress(session: ActiveSession, progress: SessionProgress) = Unit
        override fun ended(outcome: SessionOutcome) {
            if (failEndCalls > 0) { failEndCalls--; throw IllegalStateException("Simulated system failure") }
            ended += outcome
        }
    }
}
