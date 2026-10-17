package com.ray.phnomy.data

import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.AppSettings
import com.ray.phnomy.core.FocusMode
import com.ray.phnomy.core.FocusPlan
import com.ray.phnomy.core.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionJsonTest {
    @Test fun `settings and allowlist survive JSON escaping without delimiter ambiguities`() {
        val original = AppSettings(
            mode = FocusMode.POMODORO,
            focusMinutes = 45,
            shortBreakMinutes = 10,
            longBreakMinutes = 25,
            rounds = 8,
            longBreakEvery = 3,
            allowedPackages = setOf("com.example.main", "quotes\"slashes\\and,commas", "臺灣\napp", "semi;colon|pipe"),
            quietMode = true,
            theme = ThemeMode.DARK,
            accessibilityConsent = true,
        )
        assertEquals(original, SessionJson.decodeSettings(SessionJson.encodeSettings(original)))
    }

    @Test fun `active snapshot roundtrips committed plan and exit checkpoint exactly`() {
        val original = session().copy(
            plan = FocusPlan(FocusMode.POMODORO, 50, 10, 30, 6, 3, setOf("com.app.one", "com.app.two"), true),
            exitReason = "需要處理\"緊急\"事情\n第二行",
            exitRequestedAtElapsedMillis = 400_000,
            checkpointElapsedMillis = 410_000,
            checkpointFocusedMillis = 300_000,
        )
        assertEquals(original, SessionJson.decodeActive(SessionJson.encodeActive(original)))
    }

    @Test fun `optional fields added after first storage revision have safe backward defaults`() {
        val value = JSONObject(SessionJson.encodeActive(session()))
        value.remove("exitReason")
        value.remove("exitRequestedAtElapsedMillis")
        value.remove("checkpointElapsedMillis")
        value.remove("checkpointFocusedMillis")
        value.getJSONObject("plan").remove("longBreakEvery")
        value.getJSONObject("plan").remove("quietMode")
        val decoded = SessionJson.decodeActive(value.toString())
        assertEquals("", decoded.exitReason)
        assertEquals(null, decoded.exitRequestedAtElapsedMillis)
        assertEquals(decoded.startedAtElapsedMillis, decoded.checkpointElapsedMillis)
        assertEquals(0L, decoded.checkpointFocusedMillis)
        assertEquals(4, decoded.plan.longBreakEvery)
        assertFalse(decoded.plan.quietMode)
    }

    @Test fun `missing settings and unknown enum values retain valid defaults`() {
        assertEquals(AppSettings(), SessionJson.decodeSettings("{}"))
        val decoded = SessionJson.decodeSettings("""{"mode":"NEW_MODE","theme":"UNKNOWN","quietMode":"true","accessibilityConsent":3}""")
        assertEquals(FocusMode.SINGLE, decoded.mode)
        assertEquals(ThemeMode.SYSTEM, decoded.theme)
        assertFalse(decoded.quietMode)
        assertFalse(decoded.accessibilityConsent)
    }

    @Test fun `invalid setting bounds are clamped and malformed values use defaults`() {
        val decoded = SessionJson.decodeSettings("""{
            "focusMinutes":-99,"shortBreakMinutes":999,"longBreakMinutes":0,
            "rounds":10000000000,"longBreakEvery":0,"allowedPackages":["com.real",null,42,"", "com.real"]
        }""")
        assertEquals(1, decoded.focusMinutes)
        assertEquals(60, decoded.shortBreakMinutes)
        assertEquals(1, decoded.longBreakMinutes)
        assertEquals(12, decoded.rounds)
        assertEquals(1, decoded.longBreakEvery)
        assertEquals(setOf("com.real"), decoded.allowedPackages)
        assertEquals(25, SessionJson.decodeSettings("""{"focusMinutes":"NaN"}""").focusMinutes)
    }

    @Test fun `normalization applies to all stored setting boundaries`() {
        val decoded = SessionJson.decodeSettings(SessionJson.encodeSettings(AppSettings(
            focusMinutes = 999,
            shortBreakMinutes = -5,
            longBreakMinutes = 500,
            rounds = -1,
            longBreakEvery = 20,
            allowedPackages = setOf("", " ", "com.safe"),
        )))
        assertEquals(180, decoded.focusMinutes)
        assertEquals(1, decoded.shortBreakMinutes)
        assertEquals(90, decoded.longBreakMinutes)
        assertEquals(1, decoded.rounds)
        assertEquals(12, decoded.longBreakEvery)
        assertEquals(setOf("com.safe"), decoded.allowedPackages)
    }

    @Test fun `required active timing identifiers and plan cannot be silently defaulted`() {
        for (key in listOf("id", "startedAtMillis", "startedAtElapsedMillis", "bootCount", "plan")) {
            val value = JSONObject(SessionJson.encodeActive(session())).apply { remove(key) }
            rejects { SessionJson.decodeActive(value.toString()) }
        }
        rejects { SessionJson.decodeActive("garbled JSON") }
        rejects { SessionJson.decodeSettings("garbled JSON") }
    }

    @Test fun `invalid committed plan is rejected rather than shortened during recovery`() {
        for ((key, invalid) in listOf("focusMinutes" to 0, "focusMinutes" to 181, "shortBreakMinutes" to 61,
            "longBreakMinutes" to 91, "rounds" to 13, "longBreakEvery" to 0)) {
            val value = JSONObject(SessionJson.encodeActive(session()))
            value.getJSONObject("plan").put(key, invalid)
            rejects { SessionJson.decodeActive(value.toString()) }
        }
        val value = JSONObject(SessionJson.encodeActive(session()))
        value.getJSONObject("plan").put("mode", "FUTURE_MODE")
        rejects { SessionJson.decodeActive(value.toString()) }
        for (key in listOf("mode", "focusMinutes")) {
            val incomplete = JSONObject(SessionJson.encodeActive(session()))
            incomplete.getJSONObject("plan").remove(key)
            rejects { SessionJson.decodeActive(incomplete.toString()) }
        }
        for (key in listOf("shortBreakMinutes", "longBreakMinutes", "rounds", "longBreakEvery")) {
            val incomplete = JSONObject(SessionJson.encodeActive(session().copy(plan = FocusPlan(mode = FocusMode.POMODORO))))
            incomplete.getJSONObject("plan").remove(key)
            rejects { SessionJson.decodeActive(incomplete.toString()) }
        }
    }

    @Test fun `malformed checkpoints and invalid pending exits cannot recover as active`() {
        val invalidValues = listOf(
            "id" to " ",
            "startedAtElapsedMillis" to -1,
            "startedAtMillis" to "123",
            "checkpointElapsedMillis" to 0,
            "checkpointFocusedMillis" to -1,
            "checkpointFocusedMillis" to 9_999_999_999L,
            "exitRequestedAtElapsedMillis" to 200_000L,
        )
        for ((key, invalid) in invalidValues) {
            val value = JSONObject(SessionJson.encodeActive(session())).put(key, invalid)
            rejects { SessionJson.decodeActive(value.toString()) }
        }
    }

    @Test fun `single session planned focus ignores configured pomodoro rounds`() {
        assertEquals(25 * 60_000L, FocusPlan(rounds = 12).plannedFocusMillis())
        assertEquals(25 * 60_000L * 12, FocusPlan(mode = FocusMode.POMODORO, rounds = 12).plannedFocusMillis())
    }

    @Test fun `persisted allowlist is stable regardless of input set iteration order`() {
        val a = SessionJson.encodeSettings(AppSettings(allowedPackages = linkedSetOf("com.b", "com.a")))
        val b = SessionJson.encodeSettings(AppSettings(allowedPackages = linkedSetOf("com.a", "com.b")))
        assertEquals(a, b)
        assertTrue(a.contains("com.a"))
    }

    private fun session() = ActiveSession("session-123", FocusPlan(), 1_792_160_000_000, 100_000, 7)

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Malformed persistent state was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected strict validation failure.
        } catch (_: org.json.JSONException) {
            // Expected structural JSON failure.
        }
    }
}
