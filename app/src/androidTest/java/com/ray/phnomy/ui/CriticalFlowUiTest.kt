package com.ray.phnomy.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ray.phnomy.core.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the user-facing gates; the controller separately enforces the same rules. */
@RunWith(AndroidJUnit4::class)
class CriticalFlowUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun blankReasonCannotStartExitWait() {
        val reason=mutableStateOf("")
        var requested=0
        compose.setContent {
            PhnomyTheme(ThemeMode.LIGHT) {
                ExitReasonScreen(reason.value,{reason.value=it},onContinue={},onRequest={requested++})
            }
        }
        compose.onNodeWithText("下一步，等待 60 秒").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("   ")
        compose.onNodeWithText("下一步，等待 60 秒").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("需要處理緊急聯絡")
        compose.onNodeWithText("下一步，等待 60 秒").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(1,requested)}
    }

    @Test fun exitConfirmationStaysDisabledUntilWaitReachesZero() {
        val remaining=mutableStateOf(60_000L)
        val active=ActiveSession("session",FocusPlan(),0,0,0,exitReason="需要緊急聯絡",exitRequestedAtElapsedMillis=0)
        var confirmations=0
        compose.setContent {
            PhnomyTheme(ThemeMode.LIGHT) {
                ExitWaitScreen(active,SessionProgress(SessionPhase.FOCUS,1,1,120_000,0,120_000,120_000,0,
                    exitRemainingMillis=remaining.value),onContinue={},onConfirm={confirmations++})
            }
        }
        compose.onNode(hasText("等待結束後可確認退出") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {remaining.value=1}
        compose.onNode(hasText("等待結束後可確認退出") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {remaining.value=0}
        compose.onNodeWithText("確認提前結束").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(1,confirmations)}
    }

    @Test fun essentialAppCannotBeUncheckedWhileOrdinaryAppCan() {
        val apps=listOf(InstalledApp("essential.phone","電話",essential=true),InstalledApp("ordinary.notes","筆記"))
        var saved:Set<String>?=null
        compose.setContent {
            PhnomyTheme(ThemeMode.LIGHT) {
                AllowlistScreen(FocusUiState(settings=AppSettings(allowedPackages=setOf("ordinary.notes")),installedApps=apps),
                    onSave={saved=it},onBack={})
            }
        }
        compose.onNode(hasText("電話") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsOn().assertIsNotEnabled()
        compose.onNode(hasText("筆記") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("儲存白名單").performClick()
        compose.runOnIdle {assertEquals(emptySet<String>(),saved)}
    }

    @Test fun timerPublishesItsChangingRemainingFraction() {
        val fraction=mutableStateOf(0.8f)
        compose.setContent {
            PhnomyTheme(ThemeMode.LIGHT) {
                TimerFace(60_000,"保持專注","剩餘時間",remainingFraction=fraction.value)
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.8f,0f..1f))).assertExists()
        compose.runOnIdle {fraction.value=0.3f}
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.3f,0f..1f))).assertExists()
    }
}
