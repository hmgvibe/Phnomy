package com.ray.phnomy.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ray.phnomy.core.PermissionStatus
import com.ray.phnomy.core.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Completion alerts are offered without making notification consent a focus requirement. */
@RunWith(AndroidJUnit4::class)
class CompletionReminderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingNotificationConsentOffersReminderAndAllowsStartingWithoutIt() {
        val permissions = mutableStateOf(PermissionStatus(accessibility = true, exactAlarms = true))
        var notificationRequests = 0
        var starts = 0
        var otherPermissionRequests = 0
        compose.setContent {
            PhnomyTheme(ThemeMode.LIGHT) {
                PermissionsScreen(
                    state = FocusUiState(permissions = permissions.value, loaded = true),
                    onBack = {},
                    onAccessibility = { otherPermissionRequests++ },
                    onExact = { otherPermissionRequests++ },
                    onUsage = { otherPermissionRequests++ },
                    onDnd = { otherPermissionRequests++ },
                    onNotifications = { notificationRequests++ },
                    onStart = { starts++ },
                )
            }
        }

        compose.onNodeWithText("讓計時結束時提醒你").assertExists()
        compose.onNodeWithText("開啟計時結束提醒")
            .performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, notificationRequests)
            assertEquals(0, starts)
            assertEquals(0, otherPermissionRequests)
        }

        compose.onNodeWithText("暫不開啟，開始專注")
            .performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, notificationRequests)
            permissions.value = permissions.value.copy(notifications = true)
        }

        compose.onNodeWithText("開啟計時結束提醒").assertDoesNotExist()
        compose.onNodeWithText("暫不開啟，開始專注").assertDoesNotExist()
        compose.onNodeWithText("準備好了，開始專注")
            .performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, starts)
            assertEquals(1, notificationRequests)
            assertEquals(0, otherPermissionRequests)
        }
    }
}
