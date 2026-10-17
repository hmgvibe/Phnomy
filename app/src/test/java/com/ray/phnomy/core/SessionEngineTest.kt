package com.ray.phnomy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {
    private val minute = SessionEngine.MINUTE

    @Test fun `single session ignores configured pomodoro round count`() {
        val plan = FocusPlan(focusMinutes = 50, rounds = 12)
        assertEquals(listOf(SessionEngine.Stage(SessionPhase.FOCUS, 1, 50 * minute)), SessionEngine.stages(plan))
        assertEquals(50 * minute, SessionEngine.totalMillis(plan))
        assertEquals(50 * minute, SessionEngine.plannedFocusMillis(plan))
    }

    @Test fun `default pomodoro includes final long break and one hundred focused minutes`() {
        val plan = FocusPlan(mode = FocusMode.POMODORO)
        val stages = SessionEngine.stages(plan)
        assertEquals(listOf(SessionPhase.FOCUS, SessionPhase.SHORT_BREAK, SessionPhase.FOCUS, SessionPhase.SHORT_BREAK,
            SessionPhase.FOCUS, SessionPhase.SHORT_BREAK, SessionPhase.FOCUS, SessionPhase.LONG_BREAK), stages.map { it.phase })
        assertEquals(4, stages.last().round)
        assertEquals(15 * minute, stages.last().durationMillis)
        assertEquals(130 * minute, SessionEngine.totalMillis(plan))
        assertEquals(100 * minute, SessionEngine.plannedFocusMillis(plan))
    }

    @Test fun `a final round outside the long break interval ends after focus`() {
        val plan = FocusPlan(mode = FocusMode.POMODORO, rounds = 3)
        assertEquals(SessionPhase.FOCUS, SessionEngine.stages(plan).last().phase)
        assertEquals(85 * minute, SessionEngine.totalMillis(plan))
        val oneRound = plan.copy(rounds = 1)
        assertEquals(25 * minute, SessionEngine.totalMillis(oneRound))
    }

    @Test fun `custom long break interval applies at each cycle boundary`() {
        val plan = FocusPlan(FocusMode.POMODORO, focusMinutes = 10, shortBreakMinutes = 2, longBreakMinutes = 7, rounds = 5, longBreakEvery = 2)
        val stages = SessionEngine.stages(plan)
        assertEquals(listOf(2, 4), stages.filter { it.phase == SessionPhase.LONG_BREAK }.map { it.round })
        assertEquals(listOf(1, 3), stages.filter { it.phase == SessionPhase.SHORT_BREAK }.map { it.round })
        assertEquals(68 * minute, SessionEngine.totalMillis(plan))
    }

    @Test fun `exact boundaries enter next phase without a duplicated focus second`() {
        val active = session(FocusPlan(mode = FocusMode.POMODORO))
        val before = at(active, 25 * minute - 1)
        assertEquals(SessionPhase.FOCUS, before.phase)
        assertEquals(1L, before.remainingMillis)
        assertEquals(0, before.completedRounds)
        val rest = at(active, 25 * minute)
        assertEquals(SessionPhase.SHORT_BREAK, rest.phase)
        assertEquals(5 * minute, rest.remainingMillis)
        assertEquals(25 * minute, rest.focusedMillis)
        assertEquals(1, rest.completedRounds)
        val second = at(active, 30 * minute)
        assertEquals(SessionPhase.FOCUS, second.phase)
        assertEquals(2, second.round)
        assertEquals(25 * minute, second.remainingMillis)
        assertEquals(25 * minute, second.focusedMillis)
    }

    @Test fun `rest time does not increase focused time`() {
        val active = session(FocusPlan(mode = FocusMode.POMODORO))
        assertEquals(25 * minute, at(active, 27 * minute).focusedMillis)
        assertEquals(27 * minute, at(active, 32 * minute).focusedMillis)
        assertEquals(100 * minute, at(active, 120 * minute).focusedMillis)
    }

    @Test fun `default last long break remains active until its complete deadline`() {
        val active = session(FocusPlan(mode = FocusMode.POMODORO))
        val finalBreak = at(active, 115 * minute)
        assertEquals(SessionPhase.LONG_BREAK, finalBreak.phase)
        assertFalse(finalBreak.finished)
        assertEquals(4, finalBreak.completedRounds)
        assertEquals(15 * minute, finalBreak.totalRemainingMillis)
        assertFalse(at(active, 130 * minute - 1).finished)
        assertTrue(at(active, 130 * minute).finished)
        assertEquals(100 * minute, at(active, 130 * minute).focusedMillis)
    }

    @Test fun `timer and phase deadline use elapsed clock despite wall clock edits`() {
        val active = session()
        val now = ClockReading(wallMillis = active.startedAtMillis - 10_000_000, elapsedMillis = active.startedAtElapsedMillis + 4 * minute, bootCount = 9)
        val progress = SessionEngine.progress(active, now)
        assertEquals(21 * minute, progress.remainingMillis)
        assertEquals(4 * minute, progress.focusedMillis)
        assertEquals(active.startedAtElapsedMillis + 25 * minute, progress.phaseDeadlineElapsedMillis)
        val history = SessionEngine.result(active, now, SessionOutcome.EXITED)
        assertEquals(active.startedAtMillis + 4 * minute, history.endedAtMillis)
    }

    @Test fun `elapsed values clamp to valid session range`() {
        val active = session()
        assertEquals(25 * minute, at(active, -1).remainingMillis)
        val finished = at(active, 1_000 * minute)
        assertTrue(finished.finished)
        assertEquals(0L, finished.totalRemainingMillis)
        assertEquals(25 * minute, finished.focusedMillis)
        assertEquals(1, finished.completedRounds)
    }

    @Test fun `exit countdown is exactly sixty elapsed seconds and never negative`() {
        val active = session().copy(exitReason = "急事", exitRequestedAtElapsedMillis = 110_000)
        assertEquals(60_000L, at(active, 10_000).exitRemainingMillis)
        assertEquals(1L, at(active, 69_999).exitRemainingMillis)
        assertEquals(0L, at(active, 70_000).exitRemainingMillis)
        assertEquals(0L, at(active, 90_000).exitRemainingMillis)
    }

    @Test fun `boot count change and elapsed rollback are detected`() {
        val active = session()
        assertFalse(SessionEngine.bootChanged(active, ClockReading(0, 200_000, 9)))
        assertTrue(SessionEngine.bootChanged(active, ClockReading(0, 200_000, 10)))
        assertTrue(SessionEngine.bootChanged(active, ClockReading(0, active.startedAtElapsedMillis - 1, 9)))
    }

    @Test fun `reboot interruption preserves only checkpoint focus and checkpoint end time`() {
        val active = session(FocusPlan(mode = FocusMode.POMODORO)).copy(
            checkpointElapsedMillis = 100_000 + 32 * minute,
            checkpointFocusedMillis = 27 * minute,
        )
        val history = SessionEngine.result(active, ClockReading(9_000_000, 50_000, 10), SessionOutcome.INTERRUPTED, "重新啟動")
        assertEquals(SessionOutcome.INTERRUPTED, history.outcome)
        assertEquals(27 * minute, history.focusedMillis)
        assertEquals(active.startedAtMillis + 32 * minute, history.endedAtMillis)
        assertEquals(1, history.completedRounds)
        assertEquals("重新啟動", history.interruptionReason)
    }

    @Test fun `successful completion records exact planned focus rather than late alarm time`() {
        val active = session(FocusPlan(mode = FocusMode.POMODORO))
        val history = SessionEngine.result(active, ClockReading(99_000_000, active.startedAtElapsedMillis + 180 * minute, 9), SessionOutcome.COMPLETED)
        assertEquals(100 * minute, history.focusedMillis)
        assertEquals(active.startedAtMillis + 130 * minute, history.endedAtMillis)
        assertEquals(4, history.completedRounds)
        assertEquals("", history.exitReason)
    }

    @Test fun `all numeric limits are validated including inactive mode settings`() {
        assertNull(SessionEngine.validationProblem(FocusPlan(focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 1, rounds = 1, longBreakEvery = 1)))
        assertNull(SessionEngine.validationProblem(FocusPlan(focusMinutes = 180, shortBreakMinutes = 60, longBreakMinutes = 90, rounds = 12, longBreakEvery = 12)))
        for (invalid in listOf(FocusPlan(focusMinutes = 0), FocusPlan(focusMinutes = 181), FocusPlan(shortBreakMinutes = 0),
            FocusPlan(shortBreakMinutes = 61), FocusPlan(longBreakMinutes = 0), FocusPlan(longBreakMinutes = 91),
            FocusPlan(rounds = 0), FocusPlan(rounds = 13), FocusPlan(longBreakEvery = 0), FocusPlan(longBreakEvery = 13))) {
            assertNotNull(SessionEngine.validationProblem(invalid))
        }
    }

    private fun session(plan: FocusPlan = FocusPlan()) = ActiveSession("test", plan, 1_000_000, 100_000, 9)
    private fun at(session: ActiveSession, delta: Long) = SessionEngine.progress(session,
        ClockReading(session.startedAtMillis + delta, session.startedAtElapsedMillis + delta, session.bootCount))
}
