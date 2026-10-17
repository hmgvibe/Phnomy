package com.ray.phnomy.platform

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Application
import android.app.AutomaticZenRule
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.net.Uri
import android.provider.Settings
import android.service.notification.ZenPolicy
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import com.ray.phnomy.core.SessionOutcome
import com.ray.phnomy.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 36], application = Application::class)
class PlatformProvidersTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabledAccessibilityServiceList(emptyList())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test fun `app and core safety packages are essential but arbitrary system apps are not`() {
        val apps = InstalledApps(context)
        assertTrue(apps.isEssential(context.packageName))
        assertTrue(apps.isEssential("android"))
        assertTrue(apps.isEssential("com.android.systemui"))
        assertFalse(apps.isEssential("com.android.calculator2"))
        assertFalse(apps.isEssential("com.android.social"))
    }

    @Test fun `resolved launcher settings and dialer are essential`() {
        register(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "vendor.launcher")
        register(Intent(Settings.ACTION_SETTINGS), "vendor.settings")
        register(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")), "vendor.phone")
        val apps = InstalledApps(context)
        assertTrue(apps.isEssential("vendor.launcher"))
        assertTrue(apps.isEssential("vendor.settings"))
        assertTrue(apps.isEssential("vendor.phone"))
        assertFalse(apps.isEssential("vendor.unrelated"))
    }

    @Test fun `accessibility checks our component and does not accept another service`() {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val other = AccessibilityServiceInfo().apply {
            ReflectionHelpers.setField(this, "mComponentName", ComponentName("other.app", "other.app.Accessibility"))
        }
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(other))
        assertFalse(PermissionAccess(context).status().accessibility)
        val own = AccessibilityServiceInfo().apply {
            ReflectionHelpers.setField(this, "mComponentName", ComponentName(context, FocusAccessibilityService::class.java))
        }
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(other, own))
        assertTrue(PermissionAccess(context).status().accessibility)
    }

    @Test fun `exact alarms are not assumed granted`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(PermissionAccess(context).status().exactAlarms)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(PermissionAccess(context).status().exactAlarms)
    }

    @Test fun `usage permission denial produces unavailable data rather than a fake usage result`() {
        val ops = context.getSystemService(AppOpsManager::class.java)
        shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        val result = UsageReader(context).read(1_000, 5_000)
        assertFalse(result.permissionGranted)
        assertTrue(result.apps.isEmpty())
    }

    @Test fun `notification setting and runtime grant are both respected`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        grantNotifications()
        assertFalse(PermissionAccess(context).status().notifications)
        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(PermissionAccess(context).status().notifications)
        if (Build.VERSION.SDK_INT >= 33) {
            shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            assertFalse(PermissionAccess(context).status().notifications)
        }
    }

    @Test fun `only completed sessions post an independent completion notification`() {
        grantNotifications()
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        val effects = AndroidSessionEffects(context)
        effects.ended(SessionOutcome.EXITED)
        effects.ended(SessionOutcome.INTERRUPTED)
        assertNull(shadowOf(manager).getNotification(413))
        effects.ended(SessionOutcome.COMPLETED)
        val completed = shadowOf(manager).getNotification(413)
        assertNotNull(completed)
        assertEquals("focus_time_up_v2", completed.channelId)
        assertEquals("專注時間到", completed.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(0, completed.flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(completed.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test fun `completion remains silent when app notifications are disabled`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        AndroidSessionEffects(context).ended(SessionOutcome.COMPLETED)
        assertNull(shadowOf(manager).getNotification(413))
    }

    @Test fun `each new focus session refreshes only own rule with current call exceptions`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        val unrelated = AutomaticZenRule("Other rule", null, ComponentName(context, MainActivity::class.java),
            Uri.parse("condition://${context.packageName}/unrelated"), ZenPolicy.Builder().allowAlarms(false).build(),
            NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
        val unrelatedId = manager.addAutomaticZenRule(unrelated)
        manager.notificationPolicy = NotificationManager.Policy(
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY, NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS)
        val effects = AndroidSessionEffects(context)
        effects.activateQuietMode(true)
        val id = context.getSharedPreferences("phnomy_platform", Context.MODE_PRIVATE).getString("focus_zen_rule_id", null)!!
        val first = manager.getAutomaticZenRule(id)!!.zenPolicy!!
        assertEquals(ZenPolicy.PEOPLE_TYPE_ANYONE, first.priorityCallSenders)
        assertEquals(ZenPolicy.STATE_ALLOW, first.priorityCategoryRepeatCallers)
        assertEquals(ZenPolicy.STATE_ALLOW, first.priorityCategoryAlarms)
        effects.activateQuietMode(false)
        manager.notificationPolicy = NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,
            NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS, NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS)
        effects.activateQuietMode(true)
        val second = manager.getAutomaticZenRule(id)!!.zenPolicy!!
        assertEquals(ZenPolicy.PEOPLE_TYPE_CONTACTS, second.priorityCallSenders)
        assertEquals(ZenPolicy.STATE_DISALLOW, second.priorityCategoryRepeatCallers)
        assertEquals(ZenPolicy.STATE_ALLOW, second.priorityCategoryAlarms)
        assertEquals(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS, manager.notificationPolicy.priorityCategories)
        assertEquals(NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS, manager.notificationPolicy.priorityCallSenders)
        assertEquals("Other rule", manager.getAutomaticZenRule(unrelatedId)!!.name)
        assertEquals(ZenPolicy.STATE_DISALLOW, manager.getAutomaticZenRule(unrelatedId)!!.zenPolicy!!.priorityCategoryAlarms)
    }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= 33) shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Suppress("DEPRECATION")
    private fun register(intent: Intent, pkg: String) {
        val info = ResolveInfo().apply {
            isDefault = true
            activityInfo = ActivityInfo().apply {
                packageName = pkg; name = "$pkg.Main"
                applicationInfo = ApplicationInfo().apply { packageName = pkg; flags = ApplicationInfo.FLAG_SYSTEM }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }
}
