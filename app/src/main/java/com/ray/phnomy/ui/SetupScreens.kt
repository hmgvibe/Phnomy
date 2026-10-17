package com.ray.phnomy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ray.phnomy.core.*

fun Modifier.semanticsDescription(value:String)=semantics {contentDescription=value}

@Composable
fun AllowlistScreen(state:FocusUiState,onSave:(Set<String>)->Unit,onBack:()->Unit) {
    var selected by remember(state.settings.allowedPackages) {mutableStateOf(state.settings.allowedPackages)}
    var search by rememberSaveable {mutableStateOf("")}
    val apps=remember(state.installedApps,search) {state.installedApps.filter {search.isBlank()||it.label.contains(search,true)||it.packageName.contains(search,true)}}
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
            item {
                Stack {
                    PageHeader("把必要的 App 留下","專注與休息期間，只允許使用這份清單。",onBack)
                    OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                        placeholder={Text("搜尋 App")},leadingIcon={FigmaAsset("26e95",size=20.dp)},shape=RoundedCornerShape(20.dp),
                        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=palette.surface,focusedContainerColor=palette.surface,
                            unfocusedBorderColor=androidx.compose.ui.graphics.Color.Transparent))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("已選取 ${selected.size} 個 App",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=palette.primary)
                        Badge("保留必要聯繫")
                    }
                    if(state.active!=null) Notice("白名單已鎖定","進行中的專注不能擴充白名單；請在結束後調整。")
                }
                Spacer(Modifier.height(12.dp))
            }
            if(apps.isEmpty()) item {Text(if(state.installedApps.isEmpty())"正在讀取可開啟的 App…" else "找不到符合的 App",Modifier.padding(vertical=24.dp),color=palette.muted)}
            items(apps,key={it.packageName}) { app ->
                val checked=app.essential||app.packageName in selected
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).toggleable(value=checked,
                    enabled=!app.essential&&state.active==null,role=Role.Checkbox,
                    onValueChange={selected=if(it)selected+app.packageName else selected-app.packageName})
                    .padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AppIcon(app)
                    Column(Modifier.weight(1f)) {
                        Text(app.label,style=MaterialTheme.typography.titleMedium)
                        Text(if(app.essential)"必要的系統功能 · 始終允許" else if(checked)"專注期間允許使用" else "專注期間限制使用",
                            style=MaterialTheme.typography.bodySmall,color=palette.muted)
                    }
                    Checkbox(checked=checked,onCheckedChange=null,enabled=!app.essential&&state.active==null)
                }
                HorizontalDivider(color=palette.line)
            }
            item {Spacer(Modifier.height(12.dp)); Notice("必要的系統操作始終保留","系統設定、權限管理與緊急撥號仍可使用。新安裝的 App 預設受限制。")}
        }
        ActionButton("儲存白名單",{onSave(selected)},enabled=state.active==null)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PermissionsScreen(state:FocusUiState,onBack:()->Unit,onAccessibility:()->Unit,onExact:()->Unit,
    onUsage:()->Unit,onDnd:()->Unit,onNotifications:()->Unit,onStart:()->Unit) {
    val p=state.permissions
    Page {
        Stack {
            PageHeader("讓專注順利開始","只開啟你需要的功能，資料都留在手機。",onBack)
            Notice("只辨識正在使用的 App","辨識前景與可見視窗的 App 套件，用於執行白名單；不取得聊天內容或輸入文字。請確認同意後，再前往系統開啟服務。",icon=true)
            Text("開始專注前必須開啟",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            PermissionRow("無障礙服務","攔截白名單以外的 App",p.accessibility,"b243d",onAccessibility)
            PermissionRow("準確鬧鐘","準時切換專注與休息階段",p.exactAlarms,"c71fe",onExact)
            Text("選用功能",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            SheetCard {
                SettingsRow("計時結束提醒",if(p.notifications)"已允許 · 通知設定" else "開啟通知、提示音與振動",onClick=onNotifications)
                SettingsRow("使用情況存取",if(p.usage)"已允許" else "查看手機與各 App 使用時間",onClick=onUsage)
                SettingsRow("勿擾存取",if(p.dnd)"已允許" else "降低通知干擾，保留系統例外",onClick=onDnd)
            }
            if(state.settings.quietMode&&!p.dnd) Notice("勿擾尚未授權","若要使用勿擾，請開啟勿擾存取；也可返回首頁關閉勿擾選項。")
            if(!p.notifications) Notice("讓計時結束時提醒你","允許通知後，專注完成與番茄鐘階段切換時會顯示提醒。聲音與振動依系統通知及勿擾設定。")
        }
        Stack {
            Spacer(Modifier.height(24.dp))
            when {
                !p.accessibility -> ActionButton("同意並前往開啟服務",onAccessibility)
                !p.exactAlarms -> ActionButton("前往開啟準確鬧鐘",onExact)
                state.settings.quietMode&&!p.dnd -> ActionButton("前往開啟勿擾存取",onDnd)
                !p.notifications -> {
                    ActionButton("開啟計時結束提醒",onNotifications)
                    ActionButton("暫不開啟，開始專注",onStart,secondary=true,enabled=state.loaded&&state.active==null)
                }
                else -> ActionButton("準備好了，開始專注",onStart,enabled=state.loaded&&state.active==null)
            }
            Text("你可隨時從系統設定撤銷授權。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
        }
    }
}

@Composable
private fun PermissionRow(title:String,subtitle:String,granted:Boolean,icon:String,onClick:()->Unit) {
    SheetCard {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FigmaAsset(icon,size=22.dp)
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=palette.muted)
            }
            TextButton(onClick=onClick) { Text(if(granted)"已開啟" else "開啟",style=MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun SettingsScreen(state:FocusUiState,onPermissions:()->Unit,onUsage:()->Unit,onDnd:()->Unit,
    onPomo:()->Unit,onTheme:()->Unit,onClear:()->Unit) {
    val s=state.settings
    Page {
        Stack(gap=12.dp) {
            PageHeader("照自己的節奏","設定")
            Text("權限與服務",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            SheetCard {
                SettingsRow("App 攔截服務",if(state.permissions.accessibility)"已啟用" else "未啟用","3aa95",onPermissions)
                HorizontalDivider(color=palette.line)
                SettingsRow("使用情況存取",if(state.permissions.usage)"已允許" else "未授權","a4a19",onUsage)
                HorizontalDivider(color=palette.line)
                SettingsRow("勿擾模式",if(state.permissions.dnd)"已允許" else "未授權","657d6",onDnd)
            }
            SheetCard(Modifier.clickable(onClick=onPomo),padding=12.dp) {
                Text("番茄鐘預設值",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        MiniSetting("專注","${s.focusMinutes} 分鐘")
                        MiniSetting("長休息","${s.longBreakMinutes} 分鐘")
                    }
                    Column(Modifier.weight(1f)) {
                        MiniSetting("短休息","${s.shortBreakMinutes} 分鐘")
                        MiniSetting("長休息間隔","每 4 輪")
                    }
                }
                MiniSetting("專注輪數","${s.rounds} 輪")
            }
            Text("偏好與資料",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            SheetCard {
                SettingsRow("外觀",when(s.theme){ThemeMode.SYSTEM->"跟隨系統";ThemeMode.LIGHT->"淺色";ThemeMode.DARK->"深色"},"729a1",onTheme)
                HorizontalDivider(color=palette.line)
                TextButton(onClick=onClear,enabled=state.active==null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    colors=ButtonDefaults.textButtonColors(contentColor=palette.warning)) {Text("清除本機資料",Modifier.fillMaxWidth())}
            }
            Text("勿擾結束時，保留系統的來電與鬧鐘例外。\n專注進行中，無法清除本次資料。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Phnomy ${com.ray.phnomy.BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                Text("完整離線・無廣告・無追蹤",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            }
        }
    }
}

@Composable
private fun MiniSetting(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=palette.muted)
        Text(value,style=MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun DurationDialog(current:Int,onDismiss:()->Unit,onSave:(Int)->Unit) {
    var value by rememberSaveable {mutableStateOf(current.toString())}
    val minutes=value.toIntOrNull()
    AlertDialog(onDismissRequest=onDismiss,title={Text("自訂專注時間")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("留一段時間，給眼前想做的事。",style=MaterialTheme.typography.bodyMedium,color=palette.muted)
            OutlinedTextField(value,onValueChange={value=it.filter(Char::isDigit).take(3)},label={Text("分鐘")},
                singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                supportingText={Text("1–180 分鐘")})
        }
    },confirmButton={TextButton(onClick={minutes?.let(onSave)},enabled=minutes!=null&&minutes in 1..180){Text("儲存時間")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable
fun PomodoroDialog(settings:AppSettings,onDismiss:()->Unit,onSave:(AppSettings)->Unit) {
    var focus by rememberSaveable {mutableStateOf(settings.focusMinutes.toString())}
    var short by rememberSaveable {mutableStateOf(settings.shortBreakMinutes.toString())}
    var long by rememberSaveable {mutableStateOf(settings.longBreakMinutes.toString())}
    var rounds by rememberSaveable {mutableStateOf(settings.rounds.toString())}
    val f=focus.toIntOrNull();val s=short.toIntOrNull();val l=long.toIntOrNull();val r=rounds.toIntOrNull()
    val valid=f!=null&&f in 1..180&&s!=null&&s in 1..60&&l!=null&&l in 1..90&&r!=null&&r in 1..12
    AlertDialog(onDismissRequest=onDismiss,title={Text("找到自己的節奏")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            NumericField("專注 / 分鐘",focus,{focus=it},"1–180 分鐘")
            NumericField("短休息 / 分鐘",short,{short=it},"1–60 分鐘")
            NumericField("長休息 / 分鐘",long,{long=it},"1–90 分鐘")
            NumericField("專注輪數",rounds,{rounds=it},"1–12 輪")
            Text("每 4 輪長休息一次。休息期間白名單仍生效；若最後一輪為第 4 輪，完成長休息後結束。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
        }
    },confirmButton={TextButton(onClick={if(valid)onSave(settings.copy(focusMinutes=f!!,shortBreakMinutes=s!!,longBreakMinutes=l!!,rounds=r!!))},enabled=valid){Text("儲存節奏")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable
private fun NumericField(label:String,value:String,onValue:(String)->Unit,hint:String) {
    OutlinedTextField(value,onValueChange={onValue(it.filter(Char::isDigit).take(3))},modifier=Modifier.fillMaxWidth(),
        label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),supportingText={Text(hint)})
}

@Composable
fun ThemeDialog(current:ThemeMode,onDismiss:()->Unit,onSelect:(ThemeMode)->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("外觀")},text={
        Column {
            listOf(ThemeMode.SYSTEM to "跟隨系統",ThemeMode.LIGHT to "淺色",ThemeMode.DARK to "深色").forEach { (theme,label)->
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable {onSelect(theme)},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=theme==current,onClick=null);Spacer(Modifier.width(12.dp));Text(label)
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
}
