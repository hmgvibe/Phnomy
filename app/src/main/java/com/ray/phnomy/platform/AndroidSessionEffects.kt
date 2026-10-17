package com.ray.phnomy.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import com.ray.phnomy.MainActivity
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.SessionEffects
import com.ray.phnomy.core.SessionOutcome
import com.ray.phnomy.core.SessionPhase
import com.ray.phnomy.core.SessionProgress
import kotlin.math.min

class AndroidSessionEffects(private val context: Context) : SessionEffects {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("phnomy_platform", Context.MODE_PRIVATE)
    private var scheduledDeadline = Long.MIN_VALUE
    private var shownNotificationKey: String? = null
    private var quietModeActive: Boolean? = null

    override fun startProblem(): String? {
        val status = PermissionAccess(context).status()
        return when {
            !status.accessibility -> "專注保護權限已關閉，請重新啟用 Phnomy 無障礙服務。"
            !FocusAccessibilityService.isConnected -> "專注保護服務尚未連線，請稍候或在系統設定中重新啟用。"
            !status.exactAlarms -> "準確計時權限已關閉，請允許「鬧鐘和提醒」。"
            else -> null
        }
    }

    override fun schedule(session: ActiveSession, progress: SessionProgress) {
        val exitAt = session.exitRequestedAtElapsedMillis?.plus(60_000)
        val now = SystemClock.elapsedRealtime()
        val next = if (exitAt != null && exitAt > now) min(progress.phaseDeadlineElapsedMillis, exitAt)
            else progress.phaseDeadlineElapsedMillis
        if (next == scheduledDeadline) return
        // A revoke kills pending exact alarms. The connected service/receiver will record
        // an interruption; an inexact alarm must not pretend to meet strict timing.
        if (!alarms.canScheduleExactAlarms()) return
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, next.coerceAtLeast(now + 1), alarmIntent())
            scheduledDeadline = next
        } catch (_: SecurityException) {
            scheduledDeadline = Long.MIN_VALUE
        }
    }

    override fun activateQuietMode(enabled: Boolean) {
        if (enabled == quietModeActive) return
        if (!notifications.isNotificationPolicyAccessGranted) {
            quietModeActive = null
            return
        }
        runCatching {
            var id = prefs.getString(ZEN_ID, null)
            if (id != null && notifications.getAutomaticZenRule(id) == null) id = null
            if (enabled) {
                val global = notifications.notificationPolicy
                val callsAllowed = global.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS != 0
                val people = when {
                    !callsAllowed -> ZenPolicy.PEOPLE_TYPE_NONE
                    global.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS -> ZenPolicy.PEOPLE_TYPE_CONTACTS
                    global.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED -> ZenPolicy.PEOPLE_TYPE_STARRED
                    else -> ZenPolicy.PEOPLE_TYPE_ANYONE
                }
                val policy = ZenPolicy.Builder()
                    .allowAlarms(true)
                    .allowCalls(people)
                    .allowRepeatCallers(global.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS != 0)
                    .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
                    .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
                    .allowEvents(false).allowReminders(false)
                    .allowMedia(true).allowSystem(true)
                    .build()
                if (id == null) {
                    val rule = AutomaticZenRule(
                        "Phnomy 專注", null, ComponentName(context, MainActivity::class.java),
                        conditionId(), policy, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true,
                    )
                    id = notifications.addAutomaticZenRule(rule)
                    prefs.edit().putString(ZEN_ID, id).apply()
                } else {
                    // Refresh the current system call/repeat exceptions for each new
                    // focus session, while changing only our own rule, never global DND.
                    val own = notifications.getAutomaticZenRule(id)
                    if (own != null) {
                        own.zenPolicy = policy
                        check(notifications.updateAutomaticZenRule(id, own))
                    }
                }
            }
            if (id != null) {
                notifications.setAutomaticZenRuleState(id, Condition(conditionId(), "Phnomy 專注", if (enabled) Condition.STATE_TRUE else Condition.STATE_FALSE))
            }
            quietModeActive = enabled
        }.onFailure { quietModeActive = null }
    }

    @SuppressLint("MissingPermission") // PermissionAccess.status checks runtime and app notification access.
    override fun showProgress(session: ActiveSession, progress: SessionProgress) {
        // Save the observed stage even when notifications are unavailable. A grant or
        // process restart must not manufacture an alert for a stage already observed.
        val stageEnded = rememberStage(session, progress)
        if (!PermissionAccess(context).status().notifications) return
        if (stageEnded) showStageEnded(session, progress)
        val key = "${session.id}:${progress.phase}:${progress.round}:${progress.phaseDeadlineElapsedMillis}:${progress.exitRemainingMillis != null}"
        if (shownNotificationKey == key) return
        ensureChannel()
        val title = when (progress.phase) {
            SessionPhase.FOCUS -> "正在專注 · 第 ${progress.round} / ${progress.rounds} 輪"
            SessionPhase.SHORT_BREAK -> "短暫休息 · 白名單仍生效"
            SessionPhase.LONG_BREAK -> "好好休息 · 白名單仍生效"
            SessionPhase.FINISHED -> "專注完成"
        }
        val content = if (progress.exitRemainingMillis != null) "退出等待中，原定計時持續進行" else "讓注意力，回到你選擇的事。"
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(content)
            .setContentIntent(activityIntent())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis() + progress.remainingMillis)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .build()
        runCatching { notifications.notify(NOTIFICATION_ID, notification) }.onSuccess { shownNotificationKey = key }
    }

    @SuppressLint("MissingPermission") // Completion is posted only after the same runtime permission check.
    override fun ended(outcome: SessionOutcome) {
        alarms.cancel(alarmIntent())
        scheduledDeadline = Long.MIN_VALUE
        shownNotificationKey = null
        notifications.cancel(NOTIFICATION_ID)
        activateQuietMode(false)
        prefs.edit().remove(STAGE_SESSION).remove(STAGE_DEADLINE).commit()
        if (outcome == SessionOutcome.COMPLETED && PermissionAccess(context).status().notifications) {
            val completed = Notification.Builder(context, timeUpChannel())
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("專注時間到")
                .setContentText("本次專注已完成。打開 Phnomy 查看本次紀錄。")
                .setContentIntent(activityIntent())
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true).setOngoing(false)
                .setWhen(System.currentTimeMillis())
                .build()
            runCatching { notifications.notify(COMPLETION_NOTIFICATION_ID, completed) }
        }
    }

    /** A durable baseline prevents initial starts, restoration, and exit changes from alerting. */
    private fun rememberStage(session: ActiveSession, progress: SessionProgress): Boolean {
        val previousSession = prefs.getString(STAGE_SESSION, null)
        val previousDeadline = prefs.getLong(STAGE_DEADLINE, Long.MIN_VALUE)
        val changed = previousSession != session.id || previousDeadline != progress.phaseDeadlineElapsedMillis
        if (!changed) return false
        // Commit before posting so a killed/recreated process cannot repeat the sound.
        prefs.edit().putString(STAGE_SESSION, session.id)
            .putLong(STAGE_DEADLINE, progress.phaseDeadlineElapsedMillis).commit()
        return previousSession == session.id && previousDeadline != Long.MIN_VALUE &&
            previousDeadline <= SystemClock.elapsedRealtime() &&
            previousDeadline < progress.phaseDeadlineElapsedMillis && !progress.finished
    }

    @SuppressLint("MissingPermission") // Caller checks runtime and app notification access.
    private fun showStageEnded(session: ActiveSession, progress: SessionProgress) {
        val (title, content) = when (progress.phase) {
            SessionPhase.FOCUS -> "休息時間到" to "第 ${progress.round} / ${progress.rounds} 輪專注已開始。白名單持續生效。"
            SessionPhase.SHORT_BREAK -> "專注時間到" to "第 ${progress.round} / ${progress.rounds} 輪已完成，開始 ${session.plan.shortBreakMinutes} 分鐘短休息。白名單仍生效。"
            SessionPhase.LONG_BREAK -> "專注時間到" to "第 ${progress.round} / ${progress.rounds} 輪已完成，開始 ${session.plan.longBreakMinutes} 分鐘長休息。白名單仍生效。"
            SessionPhase.FINISHED -> return
        }
        val notification = Notification.Builder(context, timeUpChannel())
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(content)
            .setContentIntent(activityIntent())
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true).setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .build()
        runCatching { notifications.notify(COMPLETION_NOTIFICATION_ID, notification) }
    }

    private fun timeUpChannel(): String {
        // Android cannot raise an existing channel's importance. The new ID upgrades
        // the old DEFAULT completion channel, while later user sound/block choices
        // for this new channel remain untouched. System DND is always respected.
        if (notifications.getNotificationChannel(TIME_UP_CHANNEL) != null) return TIME_UP_CHANNEL
        val legacy = notifications.getNotificationChannel(LEGACY_COMPLETION_CHANNEL)
        if (legacy != null && (legacy.hasUserSetImportance() || legacy.hasUserSetSound())) {
            // A manually silenced/blocked legacy channel is a user preference, not
            // the old default. Continue using it rather than bypassing that choice.
            return LEGACY_COMPLETION_CHANNEL
        }
        notifications.createNotificationChannel(NotificationChannel(TIME_UP_CHANNEL, "計時結束提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "專注、休息與整段番茄鐘結束時的提示"
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        })
        return TIME_UP_CHANNEL
    }

    private fun ensureChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "專注計時", NotificationManager.IMPORTANCE_LOW).apply {
            description = "專注與休息計時狀態"
            setSound(null, null)
            enableVibration(false)
        })
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(context, 1001,
        Intent(context, SessionAlarmReceiver::class.java).setAction(SessionAlarmReceiver.ACTION_DEADLINE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun activityIntent() = PendingIntent.getActivity(context, 1002,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun conditionId() = Uri.Builder().scheme("condition").authority(context.packageName).appendPath("focus").build()

    companion object {
        private const val CHANNEL = "focus_progress"
        private const val TIME_UP_CHANNEL = "focus_time_up_v2"
        private const val LEGACY_COMPLETION_CHANNEL = "focus_completed"
        private const val NOTIFICATION_ID = 412
        private const val COMPLETION_NOTIFICATION_ID = 413
        private const val ZEN_ID = "focus_zen_rule_id"
        private const val STAGE_SESSION = "time_up_stage_session"
        private const val STAGE_DEADLINE = "time_up_stage_deadline"
    }
}
