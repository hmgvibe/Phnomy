package com.ray.phnomy.core

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FocusController(
    private val repository: FocusRepository,
    private val clock: FocusClock,
    private val effects: SessionEffects,
    scope: CoroutineScope,
    private val protectionEnabled: () -> Boolean = { true },
    autoTick: Boolean = true,
    private val awaitRestoredProtection: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private val startupMutex = Mutex()
    private var startupComplete = false
    private val _progress = MutableStateFlow<SessionProgress?>(null)
    val progress: StateFlow<SessionProgress?> = _progress.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _latestResult = MutableStateFlow<FocusHistory?>(null)
    val latestResult: StateFlow<FocusHistory?> = _latestResult.asStateFlow()
    private var scheduledKey: String? = null
    private var notificationKey: String? = null
    private var quietSessionId: String? = null
    private var idleCleaned = false

    init {
        if (autoTick) scope.launch {
            awaitReady()
            while (isActive) {
                runCatching { reconcile() }.onFailure { _error.value = it.message ?: "無法更新專注狀態，請重新開啟 App。" }
                delay(1_000)
            }
        }
    }

    private suspend fun awaitReady() {
        repository.awaitReady()
        startupMutex.withLock {
            if (!startupComplete) {
                awaitRestoredProtection()
                startupComplete = true
            }
        }
    }

    suspend fun start(plan: FocusPlan): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            check(repository.active.value == null) { "已有正在進行的專注。" }
            check(repository.settings.value.accessibilityConsent) { "請先閱讀並同意無障礙服務的使用說明。" }
            require(SessionEngine.validationProblem(plan) == null) { SessionEngine.validationProblem(plan)!! }
            effects.startProblem()?.let { error(it) }
            val now = clock.read()
            val session = ActiveSession(UUID.randomUUID().toString(), plan.copy(allowedPackages = plan.allowedPackages.toSet()),
                now.wallMillis, now.elapsedMillis, now.bootCount)
            repository.saveActive(session)
            _latestResult.value = null
            idleCleaned = false
            scheduledKey = null; notificationKey = null; quietSessionId = null
            reconcileLocked(now)
        }
    }

    suspend fun requestExit(reason: String): Result<Unit> = runCatching {
        val cleaned = reason.trim()
        require(cleaned.isNotEmpty()) { "請先填寫退出原因。" }
        require(cleaned.length <= 100) { "退出原因最多 100 個字。" }
        awaitReady()
        mutex.withLock {
            val now = clock.read()
            reconcileLocked(now)
            val current = repository.active.value ?: return@withLock
            val updated = current.copy(exitReason = cleaned,
                exitRequestedAtElapsedMillis = if (current.exitReason == cleaned) current.exitRequestedAtElapsedMillis ?: now.elapsedMillis else now.elapsedMillis)
            repository.saveActive(updated)
            reconcileLocked(now)
        }
    }

    suspend fun cancelExit() {
        awaitReady()
        mutex.withLock {
            val now = clock.read()
            reconcileLocked(now)
            repository.active.value?.let { repository.saveActive(it.copy(exitReason = "", exitRequestedAtElapsedMillis = null)) }
            reconcileLocked(now)
        }
    }

    suspend fun confirmExit(): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            val now = clock.read()
            reconcileLocked(now) // Natural completion always wins.
            val current = repository.active.value ?: return@withLock
            check(current.exitReason.isNotBlank() && current.exitRequestedAtElapsedMillis != null) { "請先填寫退出原因。" }
            check(now.elapsedMillis - current.exitRequestedAtElapsedMillis >= SessionEngine.EXIT_WAIT_MILLIS) { "請等待 60 秒倒數結束。" }
            finishLocked(current, now, SessionOutcome.EXITED)
        }
    }

    suspend fun reconcile() {
        awaitReady()
        mutex.withLock { reconcileLocked(clock.read()) }
    }

    private suspend fun reconcileLocked(now: ClockReading) {
        val current = repository.active.value
        if (current == null) {
            _progress.value = null
            // A process can die after committing the result but before removing its
            // own system rule/notification. Restore the idle system state as well.
            runCatching { effects.activateQuietMode(false) }
            if (!idleCleaned) {
                runCatching { effects.ended(SessionOutcome.INTERRUPTED) }
                    .onSuccess { idleCleaned = true }
            }
            return
        }
        idleCleaned = false
        if (SessionEngine.bootChanged(current, now)) {
            finishLocked(current, now, SessionOutcome.INTERRUPTED, "手機已重新啟動，本次專注已中斷。")
            return
        }
        if (!protectionEnabled()) {
            finishLocked(current, now, SessionOutcome.INTERRUPTED, "必要的權限或專注保護服務已失效。")
            return
        }
        val progress = SessionEngine.progress(current, now)
        if (progress.finished) { finishLocked(current, now, SessionOutcome.COMPLETED); return }
        _progress.value = progress
        if (quietSessionId != current.id) {
            effects.activateQuietMode(current.plan.quietMode)
            quietSessionId = current.id
        }
        val schedule = "${current.id}:${progress.phaseDeadlineElapsedMillis}:${current.exitRequestedAtElapsedMillis}"
        if (schedule != scheduledKey) { effects.schedule(current, progress); scheduledKey = schedule }
        val notification = "${current.id}:${progress.phase}:${progress.round}:${progress.remainingMillis / 60_000}:${current.exitRequestedAtElapsedMillis != null}"
        if (notification != notificationKey) { effects.showProgress(current, progress); notificationKey = notification }
        if (now.elapsedMillis - current.checkpointElapsedMillis >= 15_000) {
            repository.saveActive(current.copy(checkpointElapsedMillis = now.elapsedMillis, checkpointFocusedMillis = progress.focusedMillis))
        }
    }

    suspend fun interrupt(reason: String) {
        awaitReady()
        mutex.withLock {
            val current = repository.active.value ?: return@withLock
            val now = clock.read()
            // An explicit protection loss must never turn into a success record.
            finishLocked(current, now, SessionOutcome.INTERRUPTED, reason)
        }
    }

    suspend fun recordBlock(packageName: String) {
        awaitReady()
        mutex.withLock {
            val now = clock.read()
            reconcileLocked(now)
            val current = repository.active.value ?: return@withLock
            if (packageName !in current.plan.allowedPackages) {
                repository.recordBlock(BlockEvent(UUID.randomUUID().toString(), current.id, packageName, now.wallMillis))
            }
        }
    }

    private suspend fun finishLocked(current: ActiveSession, now: ClockReading, outcome: SessionOutcome, reason: String = "") = withContext(NonCancellable) {
        val result = SessionEngine.result(current, now, outcome, reason)
        if (repository.finalizeActive(current.id, result)) {
            _latestResult.value = result
            _progress.value = null; scheduledKey = null; notificationKey = null; quietSessionId = null
            runCatching { effects.activateQuietMode(false) }.onFailure { _error.value = "請到系統設定確認 Phnomy 的勿擾規則已停用。" }
            runCatching { effects.ended(outcome) }.onSuccess { idleCleaned = true }
                .onFailure { _error.value = "專注紀錄已儲存，但系統提醒未能顯示。" }
        }
    }

    fun dismissError() { _error.value = null }
    fun dismissLatestResult(id: String?) {
        _latestResult.update { current -> if (current?.id == id) null else current }
    }
}
