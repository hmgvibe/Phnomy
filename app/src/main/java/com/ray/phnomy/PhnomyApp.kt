package com.ray.phnomy

import android.app.Application
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import com.ray.phnomy.core.FocusController
import com.ray.phnomy.data.LocalFocusRepository
import com.ray.phnomy.platform.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay

class PhnomyApp : Application() {
    val appGraph by lazy { AppGraph(this) }
    override fun onCreate() { super.onCreate(); appGraph }
    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as PhnomyApp).appGraph
    }
}

class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository = LocalFocusRepository(context, scope)
    val clock = AndroidClock(context)
    val permissions = PermissionAccess(context)
    val apps = InstalledApps(context)
    val usage = UsageReader(context)
    private val effects = AndroidSessionEffects(context)
    private var stoppedSessionId: String? = null
    val controller = FocusController(repository, clock, effects, scope,
        protectionEnabled = { repository.active.value?.id != stoppedSessionId && permissions.status().ready && FocusAccessibilityService.isConnected },
        awaitRestoredProtection = {
            repository.active.value?.let { active ->
                // A force-stop may leave the accessibility setting enabled even
                // though no protection was running. Do not count that gap as success.
                val restoredAfterUserStop = runCatching {
                    context.getSystemService(ActivityManager::class.java)
                        .getHistoricalProcessExitReasons(context.packageName, 0, 4)
                        .any { it.timestamp >= active.startedAtMillis && it.reason in setOf(
                            ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED) }
                }.getOrDefault(false)
                if (restoredAfterUserStop) stoppedSessionId = active.id
                if (!restoredAfterUserStop && permissions.status().ready) {
                    repeat(30) {
                        if (!FocusAccessibilityService.isConnected) delay(100)
                    }
                }
            }
        })
}
