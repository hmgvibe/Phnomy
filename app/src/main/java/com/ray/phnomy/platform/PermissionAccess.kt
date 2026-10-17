package com.ray.phnomy.platform

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.ray.phnomy.core.PermissionStatus

class PermissionAccess(private val context: Context) {
    fun status(): PermissionStatus {
        val component = ComponentName(context, FocusAccessibilityService::class.java)
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == component }
        return PermissionStatus(
            accessibility = accessibility,
            exactAlarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            notifications = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
            usage = usageGranted(),
            dnd = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
        )
    }

    fun usageGranted(): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun openAccessibility() = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openExactAlarms() = open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    fun openUsage() = open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")))
    fun openDnd() = open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    fun openNotifications() = open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    fun notificationsPrompted(): Boolean = context.getSharedPreferences("phnomy_permissions", Context.MODE_PRIVATE)
        .getBoolean("notification_prompted", false)
    fun markNotificationsPrompted() {
        context.getSharedPreferences("phnomy_permissions", Context.MODE_PRIVATE).edit()
            .putBoolean("notification_prompted", true).apply()
    }

    private fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
