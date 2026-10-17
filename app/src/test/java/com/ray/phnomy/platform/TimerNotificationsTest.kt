package com.ray.phnomy.platform

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.ClockReading
import com.ray.phnomy.core.FocusMode
import com.ray.phnomy.core.FocusPlan
import com.ray.phnomy.core.SessionEngine
import com.ray.phnomy.core.SessionOutcome
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 36], application = Application::class)
class TimerNotificationsTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var effects: AndroidSessionEffects
    private lateinit var session: ActiveSession

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        if (Build.VERSION.SDK_INT >= 33) {
            shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        context.getSharedPreferences("phnomy_platform", Context.MODE_PRIVATE).edit().clear().commit()
        effects = AndroidSessionEffects(context)
        session = ActiveSession("session-one", FocusPlan(mode = FocusMode.POMODORO,
            focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 2, rounds = 2),
            1_000, SystemClock.elapsedRealtime(), 1)
    }

    @Test fun `initial session and minute updates do not sound an end alert`() {
        showProgress()
        assertNull(timeUp())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
        showProgress()
        assertNull(timeUp())
        assertNotNull(shadowOf(manager).getNotification(412))
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel("focus_progress").importance)
    }

    @Test fun `focus ending posts a distinct high importance timer alert while keeping progress`() {
        showProgress()
        nextMinute()
        val alert = timeUp()!!
        assertEquals("專注時間到", alert.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertTrue(alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("短休息"))
        assertEquals(Notification.CATEGORY_ALARM, alert.category)
        assertNotNull(shadowOf(manager).getNotification(412))
        val channel = manager.getNotificationChannel("focus_time_up_v2")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertTrue(channel.shouldVibrate())
        assertFalse(channel.canBypassDnd())
    }

    @Test fun `rest ending clearly announces the next focus round`() {
        showProgress()
        nextMinute()
        nextMinute()
        val alert = timeUp()!!
        assertEquals("休息時間到", alert.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertTrue(alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("第 2 / 2 輪"))
    }

    @Test fun `long rest also receives a focus time up alert`() {
        session = session.copy(plan = session.plan.copy(longBreakEvery = 1))
        showProgress()
        nextMinute()
        assertTrue(timeUp()!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("2 分鐘長休息"))
    }

    @Test fun `recreated effects do not replay the current phase alert`() {
        showProgress()
        nextMinute()
        manager.cancel(413)
        effects = AndroidSessionEffects(context)
        showProgress()
        assertNull(timeUp())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        showProgress()
        assertNull(timeUp())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(40))
        showProgress()
        assertEquals("休息時間到", timeUp()!!.extras.getCharSequence(Notification.EXTRA_TITLE))
    }

    @Test fun `first observation during a restored break only establishes the baseline`() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
        showProgress()
        assertNull(timeUp())
        nextMinute()
        assertEquals("休息時間到", timeUp()!!.extras.getCharSequence(Notification.EXTRA_TITLE))
    }

    @Test fun `exit request and cancellation within a stage do not produce time up alerts`() {
        showProgress()
        session = session.copy(exitReason = "有事", exitRequestedAtElapsedMillis = SystemClock.elapsedRealtime())
        showProgress()
        assertNull(timeUp())
        session = session.copy(exitReason = "", exitRequestedAtElapsedMillis = null)
        showProgress()
        assertNull(timeUp())
    }

    @Test fun `a disabled notification phase does not replay after notification permission returns`() {
        showProgress()
        shadowOf(manager).setNotificationsEnabled(false)
        nextMinute()
        assertNull(timeUp())
        effects = AndroidSessionEffects(context)
        shadowOf(manager).setNotificationsEnabled(true)
        showProgress()
        assertNull(timeUp())
        nextMinute()
        assertEquals("休息時間到", timeUp()!!.extras.getCharSequence(Notification.EXTRA_TITLE))
    }

    @Test fun `denied runtime notification permission does not crash or replay old phase`() {
        if (Build.VERSION.SDK_INT < 33) return
        showProgress()
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        nextMinute()
        assertNull(timeUp())
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showProgress()
        assertNull(timeUp())
    }

    @Test fun `final completion cancels progress and posts one clear auto dismissable timer alert`() {
        showProgress()
        effects.ended(SessionOutcome.COMPLETED)
        assertNull(shadowOf(manager).getNotification(412))
        assertEquals("專注時間到", timeUp()!!.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("focus_time_up_v2", timeUp()!!.channelId)
        assertEquals(Notification.CATEGORY_ALARM, timeUp()!!.category)
        assertEquals(0, timeUp()!!.flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(timeUp()!!.flags and Notification.FLAG_AUTO_CANCEL != 0)
        // Idle cleanup after completion must not recreate a dismissed alert.
        manager.cancel(413)
        effects.ended(SessionOutcome.INTERRUPTED)
        assertNull(timeUp())
    }

    @Test fun `exit and interruption do not announce that time is up`() {
        showProgress()
        effects.ended(SessionOutcome.EXITED)
        assertNull(timeUp())
        session = session.copy(id = "session-two")
        showProgress()
        effects.ended(SessionOutcome.INTERRUPTED)
        assertNull(timeUp())
        session = session.copy(id = "session-three")
        showProgress()
        assertNull(timeUp())
    }

    @Test fun `untouched legacy default channel is upgraded without modifying the legacy channel`() {
        manager.createNotificationChannel(NotificationChannel("focus_completed", "專注完成", NotificationManager.IMPORTANCE_DEFAULT))
        effects.ended(SessionOutcome.COMPLETED)
        assertEquals("focus_time_up_v2", timeUp()!!.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel("focus_time_up_v2").importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel("focus_completed").importance)
    }

    @Test fun `manually lowered legacy importance remains respected without creating a louder channel`() {
        val legacy = NotificationChannel("focus_completed", "專注完成", NotificationManager.IMPORTANCE_LOW)
        // AOSP's USER_LOCKED_IMPORTANCE flag models a user's system settings change.
        ReflectionHelpers.setField(legacy, "mUserLockedFields", 0x00000004)
        assertTrue(legacy.hasUserSetImportance())
        manager.createNotificationChannel(legacy)
        effects.ended(SessionOutcome.COMPLETED)
        assertEquals("focus_completed", timeUp()!!.channelId)
        assertNull(manager.getNotificationChannel("focus_time_up_v2"))
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel("focus_completed").importance)
    }

    @Test fun `manually blocked legacy channel remains respected`() {
        val legacy = NotificationChannel("focus_completed", "專注完成", NotificationManager.IMPORTANCE_NONE)
        ReflectionHelpers.setField(legacy, "mUserLockedFields", 0x00000004)
        manager.createNotificationChannel(legacy)
        effects.ended(SessionOutcome.COMPLETED)
        assertNull(manager.getNotificationChannel("focus_time_up_v2"))
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("focus_completed").importance)
    }

    @Test fun `existing new channel keeps the users silent settings`() {
        manager.createNotificationChannel(NotificationChannel("focus_time_up_v2", "計時結束提醒", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        })
        effects.ended(SessionOutcome.COMPLETED)
        val channel = manager.getNotificationChannel("focus_time_up_v2")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    private fun nextMinute() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
        showProgress()
    }

    private fun showProgress() = effects.showProgress(session, SessionEngine.progress(session,
        ClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(), 1)))

    private fun timeUp(): Notification? = shadowOf(manager).getNotification(413)
}
