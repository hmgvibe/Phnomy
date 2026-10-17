package com.ray.phnomy.core

import kotlinx.coroutines.flow.StateFlow

enum class FocusMode { SINGLE, POMODORO }
enum class SessionPhase { FOCUS, SHORT_BREAK, LONG_BREAK, FINISHED }
enum class SessionOutcome { COMPLETED, EXITED, INTERRUPTED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class FocusPlan(
    val mode: FocusMode = FocusMode.SINGLE,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val rounds: Int = 4,
    val longBreakEvery: Int = 4,
    val allowedPackages: Set<String> = emptySet(),
    val quietMode: Boolean = false,
)

data class AppSettings(
    val mode: FocusMode = FocusMode.SINGLE,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val rounds: Int = 4,
    val longBreakEvery: Int = 4,
    val allowedPackages: Set<String> = emptySet(),
    val quietMode: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accessibilityConsent: Boolean = false,
) {
    fun plan() = FocusPlan(mode, focusMinutes, shortBreakMinutes, longBreakMinutes, rounds, longBreakEvery, allowedPackages, quietMode)
}

data class ClockReading(val wallMillis: Long, val elapsedMillis: Long, val bootCount: Int)
fun interface FocusClock { fun read(): ClockReading }

data class ActiveSession(
    val id: String,
    val plan: FocusPlan,
    val startedAtMillis: Long,
    val startedAtElapsedMillis: Long,
    val bootCount: Int,
    val exitReason: String = "",
    val exitRequestedAtElapsedMillis: Long? = null,
    val checkpointElapsedMillis: Long = startedAtElapsedMillis,
    val checkpointFocusedMillis: Long = 0,
)

data class SessionProgress(
    val phase: SessionPhase,
    val round: Int,
    val rounds: Int,
    val remainingMillis: Long,
    val focusedMillis: Long,
    val totalRemainingMillis: Long,
    val phaseDeadlineElapsedMillis: Long,
    val completedRounds: Int,
    val exitRemainingMillis: Long? = null,
) { val finished get() = phase == SessionPhase.FINISHED }

data class FocusHistory(
    val id: String,
    val mode: FocusMode,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val focusedMillis: Long,
    val plannedFocusMillis: Long,
    val completedRounds: Int,
    val outcome: SessionOutcome,
    val exitReason: String = "",
    val interruptionReason: String = "",
)

data class BlockEvent(val id: String, val sessionId: String, val packageName: String, val atMillis: Long)
data class InstalledApp(val packageName: String, val label: String, val essential: Boolean = false)
data class AppUsage(val packageName: String, val label: String, val foregroundMillis: Long)
data class UsageSummary(val totalMillis: Long, val apps: List<AppUsage>, val permissionGranted: Boolean)
data class PermissionStatus(
    val accessibility: Boolean = false,
    val exactAlarms: Boolean = false,
    val notifications: Boolean = false,
    val usage: Boolean = false,
    val dnd: Boolean = false,
) { val ready get() = accessibility && exactAlarms }

interface FocusRepository {
    val settings: StateFlow<AppSettings>
    val active: StateFlow<ActiveSession?>
    val history: StateFlow<List<FocusHistory>>
    val blocks: StateFlow<List<BlockEvent>>
    suspend fun awaitReady()
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun saveActive(session: ActiveSession?)
    /** Atomically stores a result and clears the matching active record, once. */
    suspend fun finalizeActive(sessionId: String, result: FocusHistory): Boolean
    suspend fun recordBlock(event: BlockEvent)
    /** Must reject clearing while a session is active. */
    suspend fun clearHistory()
}

interface SessionEffects {
    /** Return a user-readable reason if strict protection cannot start. */
    fun startProblem(): String?
    fun schedule(session: ActiveSession, progress: SessionProgress)
    fun activateQuietMode(enabled: Boolean)
    fun showProgress(session: ActiveSession, progress: SessionProgress)
    fun ended(outcome: SessionOutcome)
}
