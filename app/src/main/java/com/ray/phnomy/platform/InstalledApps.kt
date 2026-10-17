package com.ray.phnomy.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.ray.phnomy.core.InstalledApp
import java.text.Collator
import java.util.Locale

/** Only launchable apps are listed; there is no QUERY_ALL_PACKAGES permission. */
class InstalledApps(private val context: Context) {
    private val pm get() = context.packageManager
    private var cachedEssentials: Set<String> = emptySet()
    private var cachedSafetySurfaces: Set<String> = emptySet()
    private var essentialsAt = Long.MIN_VALUE

    @Suppress("DEPRECATION")
    fun load(): List<InstalledApp> {
        refreshEssentials(force = true)
        val collator = Collator.getInstance(Locale.TAIWAN)
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), isEssential(it.activityInfo.packageName)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    @Synchronized
    fun isEssential(packageName: String): Boolean {
        refreshEssentials()
        return packageName in cachedEssentials
    }

    @Synchronized
    fun isSafetySurface(packageName: String): Boolean {
        refreshEssentials()
        return packageName in cachedSafetySurfaces
    }

    fun launch(packageName: String) {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @Suppress("DEPRECATION")
    private fun refreshEssentials(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && essentialsAt != Long.MIN_VALUE && now - essentialsAt < 5_000) return
        val result = mutableSetOf(context.packageName, "android", "com.android.systemui")
        fun resolve(intent: Intent) {
            runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
                .getOrNull()?.takeIf { it != "android" }?.let(result::add)
        }
        listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent("android.intent.action.MANAGE_PERMISSIONS"),
            Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
            Intent("android.intent.action.EMERGENCY_DIAL"),
            Intent("android.intent.action.EMERGENCY_ASSISTANCE"),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
        ).forEach(::resolve)
        // Preserve Home even on a fresh device with multiple launchers and no
        // chosen default (resolveActivity then returns only the system chooser).
        runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0) }
            .getOrNull()?.forEach { result.add(it.activityInfo.packageName) }
        runCatching { context.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()?.let(result::add)
        runCatching { context.getSystemService(TelecomManager::class.java).systemDialerPackage }.getOrNull()?.let(result::add)
        // In-call UI may be in a separate system service, rather than the launcher activity.
        runCatching { pm.queryIntentServices(Intent("android.telecom.InCallService"), PackageManager.MATCH_SYSTEM_ONLY) }
            .getOrNull()?.forEach { result.add(it.serviceInfo.packageName) }
        cachedSafetySurfaces = result - context.packageName
        // Keyboards must remain usable for allowed apps, but an IME window must not
        // make an otherwise blocked underlying app exempt from protection.
        runCatching { context.getSystemService(InputMethodManager::class.java).enabledInputMethodList }
            .getOrNull()?.forEach { result.add(it.packageName) }
        cachedEssentials = result
        essentialsAt = now
    }
}
