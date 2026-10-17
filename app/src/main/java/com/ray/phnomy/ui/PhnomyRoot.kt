package com.ray.phnomy.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ray.phnomy.core.*

@Composable
fun PhnomyRoot(model:FocusViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var presentedSession by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    val snack=remember { SnackbarHostState() }
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.requestNotifications() }
    val activity=LocalActivity.current
    val enableNotifications:()->Unit={
        if(android.os.Build.VERSION.SDK_INT>=33 && !state.permissions.notifications &&
            (!model.notificationsPrompted() || (activity!=null && ActivityCompat.shouldShowRequestPermissionRationale(activity,android.Manifest.permission.POST_NOTIFICATIONS)))) {
            model.markNotificationsPrompted()
            notification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else model.openNotificationSettings()
    }
    LaunchedEffect(state.error) { state.error?.let { snack.showSnackbar(it); model.dismissError() } }
    LaunchedEffect(state.loaded,state.active?.id,state.openExitRequested) {
        if(state.loaded&&state.active!=null) {
            if(presentedSession!=state.active?.id) {
                presentedSession=state.active?.id
                reason=""
                if(!state.openExitRequested) page="home"
            }
            if(state.openExitRequested) {
                page=if(state.active?.exitRequestedAtElapsedMillis!=null) "exit_wait" else "exit_reason"
                model.consumeOpenExit()
            }
        }
    }
    LaunchedEffect(page,days,state.permissions.usage) { if(page=="stats") model.refreshUsage(days) }
    BackHandler(page!="home" && state.lastResult==null) {
        if(page=="exit_wait") model.cancelExit()
        page=if(page=="history")"stats" else "home"
    }
    PhnomyTheme(state.settings.theme) {
        val view=LocalView.current
        val dark=palette.dark
        SideEffect {
            (view.context as? Activity)?.let { activity ->
                WindowCompat.getInsetsController(activity.window,view).apply {
                    isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
                }
            }
        }
        val active=state.active
        val result=state.lastResult
        val showNavigation=active==null && result==null && page in listOf("home","stats","settings","history")
        Scaffold(containerColor=palette.background,contentColor=palette.text,
            snackbarHost={ SnackbarHost(snack) },contentWindowInsets=WindowInsets.safeDrawing,
            bottomBar={ if(showNavigation) BottomTabs(if(page=="history")"stats" else page) {page=it} }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
                when {
                    !state.loaded -> LoadingScreen()
                    result!=null && active==null -> ResultScreen(result,state,onHome={model.dismissResult();page="home"},
                        onHistory={model.dismissResult();page="history"})
                    active!=null && page=="exit_reason" -> ExitReasonScreen(reason,{reason=it},
                        onContinue={model.cancelExit();page="home"},onRequest={model.requestExit(reason);page="exit_wait"})
                    active!=null && (page=="exit_wait" || active.exitRequestedAtElapsedMillis!=null) -> ExitWaitScreen(active,state.progress,
                        onContinue={model.cancelExit();page="home"},onConfirm=model::confirmExit)
                    active!=null -> ActiveScreen(active,state.progress,state.installedApps,model::launchApp,
                        onExit={page="exit_reason"})
                    page=="whitelist" -> AllowlistScreen(state,onSave={ packages->
                        model.updateSettings {it.copy(allowedPackages=packages)};page="home"},onBack={page="home"})
                    page=="permissions" -> PermissionsScreen(state,
                        onBack={page="home"},onAccessibility={dialog="accessibility"},onExact=model::openExactAlarms,
                        onUsage=model::openUsage,onDnd=model::openDnd,
                        onNotifications=enableNotifications,onStart=model::start)
                    page=="stats" -> StatisticsScreen(state,days,{days=it},onHistory={page="history"},onUsage=model::openUsage)
                    page=="history" -> HistoryScreen(state,onBack={page="stats"})
                    page=="settings" -> SettingsScreen(state,onPermissions={page="permissions"},onUsage=model::openUsage,
                        onDnd=model::openDnd,onPomo={dialog="pomodoro"},onTheme={dialog="theme"},onClear={dialog="clear"})
                    else -> HomeScreen(state,onMode={ mode->model.updateSettings {it.copy(mode=mode)} },
                        onDuration={ minutes->model.updateSettings {it.copy(focusMinutes=minutes)} },
                        onCustom={dialog="custom"},onPomo={dialog="pomodoro"},onAllowlist={page="whitelist"},
                        onQuiet={ enabled->model.updateSettings {it.copy(quietMode=enabled)} },
                        onStart={page="permissions"})
                }
            }
        }
        when(dialog) {
            "custom" -> DurationDialog(state.settings.focusMinutes,onDismiss={dialog=null},onSave={value->
                model.updateSettings {it.copy(focusMinutes=value)};dialog=null})
            "pomodoro" -> PomodoroDialog(state.settings,onDismiss={dialog=null},onSave={settings->
                model.updateSettings {current->current.copy(focusMinutes=settings.focusMinutes,
                    shortBreakMinutes=settings.shortBreakMinutes,longBreakMinutes=settings.longBreakMinutes,
                    rounds=settings.rounds,longBreakEvery=4)};dialog=null})
            "theme" -> ThemeDialog(state.settings.theme,onDismiss={dialog=null},onSelect={theme->
                model.updateSettings {it.copy(theme=theme)};dialog=null})
            "clear" -> AlertDialog(onDismissRequest={dialog=null},title={Text("清除本機資料？")},
                text={Text("這會刪除所有專注紀錄與攔截紀錄，無法復原。你的白名單與偏好設定會保留。")},
                confirmButton={TextButton(onClick={model.clearHistory();dialog=null},enabled=state.active==null){Text("清除資料")}},
                dismissButton={TextButton(onClick={dialog=null}){Text("保留資料")}})
            "accessibility" -> AlertDialog(onDismissRequest={dialog=null},
                title={Text("只辨識正在使用的 App")},
                text={Text("Phnomy 使用無障礙服務辨識前景與可見視窗所屬的 App 套件，並在專注與休息期間覆蓋非白名單 App。\n\n只讀取必要的套件名稱與視窗資訊，不遍歷畫面內容、不讀取聊天或輸入文字，也不上傳資料。電話、緊急撥號、系統設定及權限管理仍可使用。\n\n你可以隨時在系統設定撤銷授權；撤銷會將進行中的專注標記為中斷。")},
                confirmButton={TextButton(onClick={model.consentAccessibility();model.openAccessibility();dialog=null}){Text("我同意，前往開啟")}},
                dismissButton={TextButton(onClick={dialog=null}){Text("稍後再說")}})
        }
    }
}

@Composable
private fun LoadingScreen() { Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
