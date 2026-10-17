package com.ray.phnomy.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtectionPolicyTest {
    private val essential = setOf("com.ray.phnomy", "system.settings", "system.phone", "system.launcher", "system.permission")
    private fun blocked(foreground: String?, visible: List<String>, allowed: Set<String> = setOf("app.notes")) =
        ProtectionPolicy.blockedPackage(foreground, visible, allowed, essential::contains)

    @Test fun `unlisted visible split pane is blocked even while allowed pane is focused`() {
        assertEquals("app.social", blocked("app.notes", listOf("app.notes", "app.social")))
    }

    @Test fun `system settings and emergency phone remain reachable over a blocked pane`() {
        listOf("system.settings", "system.phone", "system.permission", "system.launcher").forEach { pkg ->
            assertNull(blocked(pkg, listOf("app.social", pkg)))
        }
    }

    @Test fun `Phnomy pane does not exempt a restricted split pane`() {
        assertEquals("app.social", ProtectionPolicy.blockedPackage(
            "com.ray.phnomy", listOf("app.social", "com.ray.phnomy"), emptySet(),
            isEssential = essential::contains,
            isForegroundSafety = { it in essential && it != "com.ray.phnomy" },
        ))
    }

    @Test fun `a new installation outside committed whitelist is blocked`() {
        assertEquals("new.app", blocked("new.app", emptyList()))
    }

    @Test fun `all allowed and essential visible packages are permitted`() {
        assertNull(blocked("app.notes", listOf("app.notes", "system.phone")))
    }

    @Test fun `missing window metadata falls back to the foreground package`() {
        assertEquals("app.social", blocked("app.social", emptyList()))
        assertNull(blocked(null, emptyList()))
    }

    @Test fun `an essential keyboard does not exempt a blocked application underneath`() {
        assertEquals("app.social", ProtectionPolicy.blockedPackage(
            "system.keyboard", listOf("app.social"), emptySet(),
            isEssential = { it in essential || it == "system.keyboard" },
            isForegroundSafety = essential::contains,
        ))
    }
}
