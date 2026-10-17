package com.ray.phnomy.core

object SessionEngine {
    const val MINUTE = 60_000L
    const val EXIT_WAIT_MILLIS = 60_000L
    data class Stage(val phase: SessionPhase, val round: Int, val durationMillis: Long)

    fun validationProblem(plan: FocusPlan): String? = when {
        plan.focusMinutes !in 1..180 -> "專注時間須介於 1 至 180 分鐘。"
        plan.shortBreakMinutes !in 1..60 -> "短休息須介於 1 至 60 分鐘。"
        plan.longBreakMinutes !in 1..90 -> "長休息須介於 1 至 90 分鐘。"
        plan.rounds !in 1..12 -> "番茄鐘輪數須介於 1 至 12 輪。"
        plan.longBreakEvery !in 1..12 -> "長休息間隔須介於 1 至 12 輪。"
        else -> null
    }

    fun stages(plan: FocusPlan): List<Stage> {
        require(validationProblem(plan) == null)
        if (plan.mode == FocusMode.SINGLE) return listOf(Stage(SessionPhase.FOCUS, 1, plan.focusMinutes * MINUTE))
        return buildList {
            for (round in 1..plan.rounds) {
                add(Stage(SessionPhase.FOCUS, round, plan.focusMinutes * MINUTE))
                if (round % plan.longBreakEvery == 0) {
                    add(Stage(SessionPhase.LONG_BREAK, round, plan.longBreakMinutes * MINUTE))
                } else if (round < plan.rounds) {
                    add(Stage(SessionPhase.SHORT_BREAK, round, plan.shortBreakMinutes * MINUTE))
                }
            }
        }
    }

    fun plannedFocusMillis(plan: FocusPlan) = plan.focusMinutes * MINUTE * if (plan.mode == FocusMode.SINGLE) 1 else plan.rounds
    fun totalMillis(plan: FocusPlan) = stages(plan).sumOf { it.durationMillis }

    fun progress(session: ActiveSession, now: ClockReading): SessionProgress {
        val stages = stages(session.plan)
        val total = stages.sumOf { it.durationMillis }
        val elapsed = (now.elapsedMillis - session.startedAtElapsedMillis).coerceIn(0, total)
        val exitRemaining = session.exitRequestedAtElapsedMillis?.let { (it + EXIT_WAIT_MILLIS - now.elapsedMillis).coerceAtLeast(0) }
        var offset = 0L
        var focused = 0L
        var roundsDone = 0
        for (stage in stages) {
            val end = offset + stage.durationMillis
            if (elapsed < end) {
                if (stage.phase == SessionPhase.FOCUS) focused += elapsed - offset
                return SessionProgress(stage.phase, stage.round, if (session.plan.mode == FocusMode.SINGLE) 1 else session.plan.rounds,
                    end - elapsed, focused, total - elapsed, session.startedAtElapsedMillis + end, roundsDone, exitRemaining)
            }
            if (stage.phase == SessionPhase.FOCUS) { focused += stage.durationMillis; roundsDone++ }
            offset = end
        }
        return SessionProgress(SessionPhase.FINISHED, roundsDone, roundsDone, 0, focused, 0,
            session.startedAtElapsedMillis + total, roundsDone, exitRemaining)
    }

    fun bootChanged(session: ActiveSession, now: ClockReading) =
        session.bootCount != now.bootCount || now.elapsedMillis < session.startedAtElapsedMillis

    fun result(session: ActiveSession, now: ClockReading, outcome: SessionOutcome, interruption: String = ""): FocusHistory {
        val useCheckpoint = bootChanged(session, now) || outcome == SessionOutcome.INTERRUPTED
        val progress = if (useCheckpoint) null else progress(session, now)
        val focus = if (useCheckpoint) session.checkpointFocusedMillis else progress!!.focusedMillis
        val elapsed = if (useCheckpoint) (session.checkpointElapsedMillis - session.startedAtElapsedMillis).coerceAtLeast(0)
            else (now.elapsedMillis - session.startedAtElapsedMillis).coerceIn(0, totalMillis(session.plan))
        return FocusHistory(session.id, session.plan.mode, session.startedAtMillis, session.startedAtMillis + elapsed,
            focus.coerceIn(0, plannedFocusMillis(session.plan)), plannedFocusMillis(session.plan),
            progress?.completedRounds ?: (focus / (session.plan.focusMinutes * MINUTE)).toInt(), outcome,
            if (outcome == SessionOutcome.EXITED) session.exitReason else "", interruption)
    }
}
