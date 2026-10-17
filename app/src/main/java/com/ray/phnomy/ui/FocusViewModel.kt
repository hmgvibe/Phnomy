package com.ray.phnomy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ray.phnomy.AppGraph
import com.ray.phnomy.core.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FocusUiState(
    val settings: AppSettings = AppSettings(),
    val active: ActiveSession? = null,
    val progress: SessionProgress? = null,
    val history: List<FocusHistory> = emptyList(),
    val blocks: List<BlockEvent> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val permissions: PermissionStatus = PermissionStatus(),
    val usage: UsageSummary? = null,
    val loaded: Boolean = false,
    val error: String? = null,
    val lastResult: FocusHistory? = null,
    val openExitRequested: Boolean = false,
)

class FocusViewModel(private val graph: AppGraph) : ViewModel() {
    private val repository = graph.repository
    private val transient = MutableStateFlow(FocusUiState())
    private var usageDays = 7
    private val actions = Mutex()
    private val persisted = combine(repository.settings, repository.active, graph.controller.progress,
        repository.history, repository.blocks) { settings, active, progress, history, blocks ->
        FocusUiState(settings=settings,active=active,progress=progress,history=history,blocks=blocks)
    }
    val ui: StateFlow<FocusUiState> = combine(persisted, transient, graph.controller.error, graph.controller.latestResult) { stored, extra, controllerError, result ->
        stored.copy(installedApps=extra.installedApps, permissions=extra.permissions,usage=extra.usage,
            loaded=extra.loaded,error=extra.error ?: controllerError,lastResult=result,
            openExitRequested=extra.openExitRequested)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FocusUiState())

    init {
        execute {
            repository.awaitReady()
            transient.update { it.copy(loaded=true) }
            graph.controller.reconcile()
            refresh()
        }
    }
    private fun execute(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { actions.withLock { block() } }.onFailure { error ->
                transient.update { it.copy(error=error.message ?: "操作未完成，請再試一次。") }
            }
        }
    }
    fun refresh() = execute {
        repository.awaitReady()
        val permission = graph.permissions.status()
        val apps = withContext(Dispatchers.IO) { graph.apps.load() }
        transient.update { it.copy(permissions=permission,installedApps=apps) }
        graph.controller.reconcile()
        refreshUsage(usageDays)
    }
    fun updateSettings(transform: (AppSettings) -> AppSettings) = execute {
        check(repository.active.value == null) { "本次專注已鎖定，設定將在結束後開放。" }
        repository.updateSettings(transform)
    }
    fun start() = execute {
        graph.controller.start(repository.settings.value.plan()).getOrThrow()
    }
    fun requestExit(reason: String) = execute { graph.controller.requestExit(reason).getOrThrow() }
    fun cancelExit() = execute { graph.controller.cancelExit() }
    fun confirmExit() = execute { graph.controller.confirmExit().getOrThrow() }
    fun openAccessibility() = graph.permissions.openAccessibility()
    fun consentAccessibility() = execute { repository.updateSettings { it.copy(accessibilityConsent=true) } }
    fun openExactAlarms() = graph.permissions.openExactAlarms()
    fun openUsage() = graph.permissions.openUsage()
    fun openDnd() = graph.permissions.openDnd()
    fun requestNotifications() = refresh()
    fun openNotificationSettings() = graph.permissions.openNotifications()
    fun notificationsPrompted() = graph.permissions.notificationsPrompted()
    fun markNotificationsPrompted() = graph.permissions.markNotificationsPrompted()
    fun launchApp(pkg: String) {
        val active = repository.active.value
        if (active == null || pkg in active.plan.allowedPackages || graph.apps.isEssential(pkg)) graph.apps.launch(pkg)
    }
    fun refreshUsage(days: Int) = execute {
        usageDays = if (days == 1) 1 else 7
        val start = LocalDate.now().minusDays((usageDays-1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val summary = withContext(Dispatchers.IO) { graph.usage.read(start, System.currentTimeMillis()) }
        transient.update { it.copy(usage=summary) }
    }
    fun clearHistory() = execute { repository.clearHistory(); graph.controller.dismissLatestResult(graph.controller.latestResult.value?.id) }
    fun dismissError() { transient.update { it.copy(error=null) }; graph.controller.dismissError() }
    fun dismissResult() { graph.controller.dismissLatestResult(ui.value.lastResult?.id) }
    fun requestOpenExit() { transient.update { it.copy(openExitRequested=true) } }
    fun consumeOpenExit() { transient.update { it.copy(openExitRequested=false) } }
}
