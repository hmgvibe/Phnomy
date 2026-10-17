package com.ray.phnomy.platform

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Rect
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.ray.phnomy.MainActivity
import com.ray.phnomy.PhnomyApp
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Only window metadata and root.packageName are read. No text or child nodes. */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph by lazy { PhnomyApp.graph(this) }
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private val overlays = mutableMapOf<String, OverlayEntry>()
    private var foregroundPackage: String? = null
    private var observing = false

    override fun onServiceConnected() {
        isConnected = true
        if (observing) return
        observing = true
        scope.launch {
            graph.repository.awaitReady()
            while (isActive) {
                runCatching { graph.controller.reconcile() }
                evaluateForeground()
                delay(1_000)
            }
        }
        scope.launch {
            graph.repository.awaitReady()
            graph.repository.active.collect { evaluateForeground() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in WINDOW_EVENTS) return
        if (overlays.isNotEmpty() && runCatching {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.id == event.windowId }
        }.getOrDefault(false)) return
        event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { foregroundPackage = it }
        scope.launch {
            graph.repository.awaitReady()
            runCatching { graph.controller.reconcile() }
            evaluateForeground()
        }
    }

    private suspend fun evaluateForeground() {
        val active = graph.repository.active.value
        if (active == null || getSystemService(KeyguardManager::class.java).isKeyguardLocked
            || !getSystemService(PowerManager::class.java).isInteractive) {
            removeOverlays(); return
        }
        val snapshots = windowPackages()
        val foreground = snapshots.firstOrNull { it.focused }?.packageName
            ?: snapshots.firstOrNull { it.active }?.packageName ?: foregroundPackage
        if (foreground != null && graph.apps.isSafetySurface(foreground)) {
            removeOverlays(); return
        }
        val blockedWindows = snapshots.filter { it.application && !graph.apps.isEssential(it.packageName) && it.packageName !in active.plan.allowedPackages }
        val targets = if (blockedWindows.isNotEmpty()) blockedWindows else {
            ProtectionPolicy.blockedPackage(foreground, emptyList(), active.plan.allowedPackages,
                graph.apps::isEssential, graph.apps::isSafetySurface)?.let {
                listOf(WindowPackage(-1, it, true, true, true, null))
            } ?: emptyList()
        }
        val progress = graph.controller.progress.value
        if (targets.isEmpty() || progress == null || progress.finished) { removeOverlays(); return }
        val theme = graph.repository.settings.value.theme
        val dark = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM &&
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val wanted = targets.map { "${it.id}:${it.packageName}" }.toSet()
        overlays.keys.filter { it !in wanted }.toList().forEach(::removeOverlay)
        for (target in targets) {
            val key = "${target.id}:${target.packageName}"
            var entry = overlays[key]
            if (entry == null || entry.sessionId != active.id || entry.bounds != target.bounds || entry.dark != dark) {
                val previouslyBlocked = entry?.sessionId == active.id
                removeOverlay(key)
                entry = showOverlay(active, target, dark)
                if (entry != null) {
                    overlays[key] = entry
                    if (!previouslyBlocked) graph.controller.recordBlock(target.packageName)
                }
            }
            entry?.content?.update(progress)
        }
    }

    @Suppress("DEPRECATION")
    private fun windowPackages(): List<WindowPackage> = runCatching {
        windows.mapNotNull { window ->
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@mapNotNull null
            val root = window.root ?: return@mapNotNull null
            try {
                // The only node property accessed: packageName. No text, descriptions,
                // actions, child nodes, screenshots, or chat content are requested.
                val pkg = root.packageName?.toString() ?: return@mapNotNull null
                val bounds = Rect().also(window::getBoundsInScreen)
                if (bounds.isEmpty || !Rect.intersects(bounds, wm.currentWindowMetrics.bounds)) return@mapNotNull null
                WindowPackage(window.id, pkg, window.isFocused, window.isActive,
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION, bounds)
            } finally { root.recycle() }
        }
    }.getOrDefault(emptyList())

    private fun showOverlay(active: ActiveSession, target: WindowPackage, dark: Boolean): OverlayEntry? {
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val display = metrics.bounds
        val safe = Rect(display.left + insets.left, display.top + insets.top,
            display.right - insets.right, display.bottom - insets.bottom)
        val bounds = target.bounds?.let { source -> Rect(source).takeIf { it.intersect(safe) && !it.isEmpty } }
        val compact = bounds != null && bounds.width() / resources.displayMetrics.density < 320
        val content = ProtectionOverlay(this, active, target.packageName, dark, compact,
            onFocus = { openMain(false) }, onExit = { openMain(true) },
            onHome = { removeOverlays(); performGlobalAction(GLOBAL_ACTION_HOME) },
            onShortcut = { pkg -> graph.apps.launch(pkg) })
        val params = WindowManager.LayoutParams(
            bounds?.width()?.coerceAtLeast(1) ?: WindowManager.LayoutParams.MATCH_PARENT,
            bounds?.height()?.coerceAtLeast(1) ?: WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Default inset layout is relative to the safe system-bar area.
            x = (bounds?.left ?: safe.left) - safe.left
            y = (bounds?.top ?: safe.top) - safe.top
            title = "Phnomy 專注保護"
        }
        return try {
            wm.addView(content.view, params)
            OverlayEntry(active.id, target.bounds, dark, content)
        } catch (_: RuntimeException) {
            scope.launch { graph.controller.interrupt("系統無法顯示專注保護畫面。") }
            null
        }
    }

    private fun openMain(exit: Boolean) {
        // A direct user tap only; no unsolicited background Activity lock.
        runCatching {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("phnomy_open_exit", exit))
        }
        // Leave overlays on other split panes in place; Phnomy's pane is not covered.
    }

    private fun removeOverlay(key: String) {
        overlays.remove(key)?.content?.view?.let { runCatching { wm.removeViewImmediate(it) } }
    }

    private fun removeOverlays() { overlays.keys.toList().forEach(::removeOverlay) }

    override fun onInterrupt() {
        removeOverlays()
        scope.launch { graph.controller.interrupt("系統中斷了專注保護服務。") }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        removeOverlays()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            graph.repository.awaitReady()
            graph.controller.interrupt("專注保護服務已關閉。")
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        removeOverlays()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var isConnected = false
            private set
        private val WINDOW_EVENTS = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED, AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }
}

private data class WindowPackage(val id: Int, val packageName: String, val focused: Boolean,
    val active: Boolean, val application: Boolean, val bounds: Rect?)
private data class OverlayEntry(val sessionId: String, val bounds: Rect?, val dark: Boolean,
    val content: ProtectionOverlay)

/** Safety surfaces precede restrictions; the app itself is exempt but not a safety override. */
internal object ProtectionPolicy {
    fun blockedPackage(foregroundPackage: String?, visibleApplicationPackages: List<String>,
        allowedPackages: Set<String>, isEssential: (String) -> Boolean,
        isForegroundSafety: (String) -> Boolean = isEssential): String? {
        if (foregroundPackage != null && isForegroundSafety(foregroundPackage)) return null
        return (visibleApplicationPackages + listOfNotNull(foregroundPackage)).distinct()
            .firstOrNull { !isEssential(it) && it !in allowedPackages }
    }
}
