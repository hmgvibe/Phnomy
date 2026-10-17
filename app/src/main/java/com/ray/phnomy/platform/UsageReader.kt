package com.ray.phnomy.platform

import android.app.usage.UsageStatsManager
import android.content.Context
import com.ray.phnomy.core.AppUsage
import com.ray.phnomy.core.UsageSummary

/** System daily usage buckets, rather than inferred/fabricated app activity. */
class UsageReader(private val context: Context) {
    @Suppress("DEPRECATION")
    fun read(startMillis: Long, endMillis: Long): UsageSummary {
        if (!PermissionAccess(context).usageGranted()) return UsageSummary(0, emptyList(), false)
        if (endMillis <= startMillis) return UsageSummary(0, emptyList(), true)
        return try {
            val rows = context.getSystemService(UsageStatsManager::class.java)
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis)
                ?: return UsageSummary(0, emptyList(), false)
            val apps = rows.groupBy { it.packageName }.mapNotNull { (pkg, values) ->
                val millis = values.sumOf { it.totalTimeInForeground.coerceAtLeast(0) }
                if (millis <= 0) null else {
                    val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    AppUsage(pkg, label, millis)
                }
            }.sortedByDescending { it.foregroundMillis }
            UsageSummary(apps.sumOf { it.foregroundMillis }, apps, true)
        } catch (_: SecurityException) {
            UsageSummary(0, emptyList(), false)
        }
    }
}
