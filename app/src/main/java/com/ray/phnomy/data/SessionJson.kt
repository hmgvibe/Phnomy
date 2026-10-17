package com.ray.phnomy.data

import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.AppSettings
import com.ray.phnomy.core.FocusMode
import com.ray.phnomy.core.FocusPlan
import com.ray.phnomy.core.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/** The persisted JSON format is intentionally independent of a serialization plugin. */
internal object SessionJson {
    private const val VERSION = 1

    fun encodeSettings(settings: AppSettings): String {
        val normalized = settings.normalized()
        return planObject(normalized.plan())
            .put("version", VERSION)
            .put("theme", normalized.theme.name)
            .put("accessibilityConsent", normalized.accessibilityConsent)
            .toString()
    }

    fun decodeSettings(raw: String): AppSettings {
        val value = JSONObject(raw)
        val plan = readPlan(value, strict = false)
        return AppSettings(
            mode = plan.mode,
            focusMinutes = plan.focusMinutes,
            shortBreakMinutes = plan.shortBreakMinutes,
            longBreakMinutes = plan.longBreakMinutes,
            rounds = plan.rounds,
            longBreakEvery = plan.longBreakEvery,
            allowedPackages = plan.allowedPackages,
            quietMode = plan.quietMode,
            theme = enumOrDefault(value.stringValue("theme"), ThemeMode.SYSTEM),
            accessibilityConsent = value.booleanValue("accessibilityConsent", false),
        ).normalized()
    }

    fun encodeActive(session: ActiveSession): String = JSONObject()
        .put("version", VERSION)
        .put("id", session.id)
        .put("plan", planObject(session.plan))
        .put("startedAtMillis", session.startedAtMillis)
        .put("startedAtElapsedMillis", session.startedAtElapsedMillis)
        .put("bootCount", session.bootCount)
        .put("exitReason", session.exitReason)
        .put("exitRequestedAtElapsedMillis", session.exitRequestedAtElapsedMillis ?: JSONObject.NULL)
        .put("checkpointElapsedMillis", session.checkpointElapsedMillis)
        .put("checkpointFocusedMillis", session.checkpointFocusedMillis)
        .toString()

    /** Required timing fields are strict: a damaged session must never become a new success. */
    fun decodeActive(raw: String): ActiveSession {
        val value = JSONObject(raw)
        val id = value.requiredString("id").also { require(it.isNotBlank()) }
        val started = value.requiredLong("startedAtMillis").also { require(it >= 0) }
        val elapsed = value.requiredLong("startedAtElapsedMillis").also { require(it >= 0) }
        val boot = value.requiredLong("bootCount").also { require(it in -1..Int.MAX_VALUE.toLong()) }.toInt()
        val plan = readPlan(value.getJSONObject("plan"), strict = true)
        val checkpoint = if (value.has("checkpointElapsedMillis")) {
            value.requiredLong("checkpointElapsedMillis")
        } else elapsed
        val focused = if (value.has("checkpointFocusedMillis")) {
            value.requiredLong("checkpointFocusedMillis")
        } else 0L
        require(checkpoint >= elapsed)
        require(focused in 0..plan.plannedFocusMillis())
        val reason = value.stringValue("exitReason") ?: ""
        val exitAt = if (!value.has("exitRequestedAtElapsedMillis") || value.isNull("exitRequestedAtElapsedMillis")) {
            null
        } else value.requiredLong("exitRequestedAtElapsedMillis").also {
            require(it >= elapsed && reason.isNotBlank())
        }
        return ActiveSession(id, plan, started, elapsed, boot, reason, exitAt, checkpoint, focused)
    }

    fun validateActive(session: ActiveSession) {
        // Use the same validation for snapshots written by the controller and restored snapshots.
        decodeActive(encodeActive(session))
    }

    private fun planObject(plan: FocusPlan): JSONObject = JSONObject()
        .put("mode", plan.mode.name)
        .put("focusMinutes", plan.focusMinutes)
        .put("shortBreakMinutes", plan.shortBreakMinutes)
        .put("longBreakMinutes", plan.longBreakMinutes)
        .put("rounds", plan.rounds)
        .put("longBreakEvery", plan.longBreakEvery)
        .put("allowedPackages", JSONArray(plan.allowedPackages.sorted()))
        .put("quietMode", plan.quietMode)

    private fun readPlan(value: JSONObject, strict: Boolean): FocusPlan {
        val default = FocusPlan()
        fun number(key: String, fallback: Int, range: IntRange): Int {
            if (!value.has(key)) return fallback
            val raw = value.opt(key)
            if (strict) {
                require(raw is Number && raw.toDouble() == raw.toLong().toDouble()) { "Invalid $key" }
                require(raw.toLong() in range.first.toLong()..range.last.toLong()) { "Invalid $key" }
                return raw.toInt()
            }
            return (raw as? Number)?.toLong()?.coerceIn(range.first.toLong(), range.last.toLong())?.toInt() ?: fallback
        }
        val modeValue = value.stringValue("mode")
        if (strict) {
            require(modeValue != null && value.has("focusMinutes")) { "Missing committed focus plan" }
            if (modeValue == FocusMode.POMODORO.name) {
                require(listOf("shortBreakMinutes", "longBreakMinutes", "rounds", "longBreakEvery").all(value::has)) {
                    "Missing committed pomodoro schedule"
                }
            }
        }
        val mode = if (strict && modeValue != null) FocusMode.valueOf(modeValue) else enumOrDefault(modeValue, default.mode)
        val packages = if (value.has("allowedPackages")) {
            val array = value.optJSONArray("allowedPackages")
            if (strict) require(array != null) { "Invalid allowedPackages" }
            buildSet {
                if (array != null) for (index in 0 until array.length()) {
                    val item = array.opt(index)
                    if (strict) require(item is String && item.isNotBlank()) { "Invalid allowedPackages entry" }
                    if (item is String && item.isNotBlank()) add(item)
                }
            }
        } else emptySet()
        return FocusPlan(
            mode = mode,
            focusMinutes = number("focusMinutes", default.focusMinutes, 1..180),
            shortBreakMinutes = number("shortBreakMinutes", default.shortBreakMinutes, 1..60),
            longBreakMinutes = number("longBreakMinutes", default.longBreakMinutes, 1..90),
            rounds = number("rounds", default.rounds, 1..12),
            longBreakEvery = number("longBreakEvery", default.longBreakEvery, 1..12),
            allowedPackages = packages,
            quietMode = value.booleanValue("quietMode", false),
        )
    }

    private fun JSONObject.stringValue(key: String): String? = opt(key) as? String

    private fun JSONObject.booleanValue(key: String, fallback: Boolean): Boolean = opt(key) as? Boolean ?: fallback

    private fun JSONObject.requiredString(key: String): String = (get(key) as? String)
        ?: throw IllegalArgumentException("Missing or invalid $key")

    private fun JSONObject.requiredLong(key: String): Long {
        val number = get(key) as? Number ?: throw IllegalArgumentException("Missing or invalid $key")
        require(number.toDouble().isFinite() && number.toDouble() == number.toLong().toDouble()) { "Invalid $key" }
        return number.toLong()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

internal fun AppSettings.normalized(): AppSettings = copy(
    focusMinutes = focusMinutes.coerceIn(1, 180),
    shortBreakMinutes = shortBreakMinutes.coerceIn(1, 60),
    longBreakMinutes = longBreakMinutes.coerceIn(1, 90),
    rounds = rounds.coerceIn(1, 12),
    longBreakEvery = longBreakEvery.coerceIn(1, 12),
    allowedPackages = allowedPackages.filterTo(linkedSetOf()) { it.isNotBlank() },
)

internal fun FocusPlan.plannedFocusMillis(): Long = focusMinutes * 60_000L * if (mode == FocusMode.SINGLE) 1 else rounds
