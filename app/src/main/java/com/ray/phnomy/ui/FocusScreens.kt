package com.ray.phnomy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ray.phnomy.core.*
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(state:FocusUiState,onMode:(FocusMode)->Unit,onDuration:(Int)->Unit,onCustom:()->Unit,
    onPomo:()->Unit,onAllowlist:()->Unit,onQuiet:(Boolean)->Unit,onStart:()->Unit) {
    val settings=state.settings
    val today=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todayFocus=state.history.filter {it.startedAtMillis>=today}.sumOf {it.focusedMillis}
    Page {
        Stack {
            Brand()
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("把時間，\n留給眼前的事。",style=MaterialTheme.typography.headlineMedium)
                Text("少一點分心，多一點自己的時間。",style=MaterialTheme.typography.bodyMedium,color=palette.muted)
            }
            TwoChoice(listOf("單次專注","番茄鐘"),if(settings.mode==FocusMode.SINGLE)0 else 1) {
                onMode(if(it==0)FocusMode.SINGLE else FocusMode.POMODORO)
            }
            if(settings.mode==FocusMode.SINGLE) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min=128.dp),contentAlignment=Alignment.Center) {
                    val typeSize=minOf(96f,(maxWidth.value-64f)/(settings.focusMinutes.toString().length*0.61f)) / LocalDensity.current.fontScale.coerceAtLeast(1f)
                    Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        Text(settings.focusMinutes.toString(),fontFamily=RobotoMono,fontSize=typeSize.sp,lineHeight=(typeSize*1.21f).sp,color=palette.primary)
                        Spacer(Modifier.width(8.dp)); Text("分鐘",color=palette.muted)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf(25,50,0).forEach { value ->
                        val chosen=if(value==0)settings.focusMinutes !in listOf(25,50) else settings.focusMinutes==value
                        Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if(chosen)palette.soft else palette.surface)
                            .clickable {if(value==0)onCustom() else onDuration(value)}.padding(vertical=12.dp),
                            contentAlignment=Alignment.Center) {
                            Text(if(value==0)"自訂" else "$value 分鐘",style=MaterialTheme.typography.titleSmall,
                                color=if(chosen)palette.primary else palette.muted,textAlign=TextAlign.Center)
                        }
                    }
                }
            } else {
                SheetCard(Modifier.clickable(onClick=onPomo),padding=20.dp) {
                    Text("專注與休息，找到自己的節奏",style=MaterialTheme.typography.bodyMedium,color=palette.muted)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        listOf(settings.focusMinutes to "專注 / 分",settings.shortBreakMinutes to "短休息 / 分",settings.rounds to "專注輪數").forEach { (value,label) ->
                            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("$value",fontFamily=RobotoMono,fontSize=32.sp,lineHeight=39.sp,color=palette.primary)
                                Text(label,style=MaterialTheme.typography.bodySmall,color=palette.muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp)); HorizontalDivider(color=palette.line)
                    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("每 4 輪，長休息 ${settings.longBreakMinutes} 分鐘",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=palette.muted)
                        FigmaAsset("bd78b","c89ec",16.dp)
                    }
                }
            }
        }
        Stack {
            Spacer(Modifier.height(32.dp))
            SheetCard {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=onAllowlist),
                    horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    FigmaAsset("b243d","f879a",22.dp)
                    Column(Modifier.weight(1f)) {
                        Text("允許使用的 App",style=MaterialTheme.typography.titleSmall)
                        val names=state.installedApps.filter {it.packageName in settings.allowedPackages}.map {it.label}
                        Text(if(names.isEmpty())"必要的系統功能" else names.joinToString("、"),style=MaterialTheme.typography.bodySmall,
                            color=palette.muted,maxLines=2)
                    }
                    FigmaAsset("f026c","aa5a0",20.dp)
                }
                HorizontalDivider(color=palette.line)
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FigmaAsset("28f3a","d92d6",22.dp)
                    Column(Modifier.weight(1f)) {
                        Text("專注時開啟勿擾",style=MaterialTheme.typography.titleSmall)
                        Text("保留來電與鬧鐘例外",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                    }
                    Switch(checked=settings.quietMode,onCheckedChange=onQuiet,
                        modifier=Modifier.semanticsDescription("專注時開啟勿擾"),
                        colors=SwitchDefaults.colors(checkedTrackColor=palette.primary,checkedThumbColor=palette.onPrimary,
                            uncheckedTrackColor=palette.line,uncheckedThumbColor=palette.muted,uncheckedBorderColor=palette.line))
                }
            }
            ActionButton(if(settings.mode==FocusMode.SINGLE)"開始專注" else "開始番茄鐘",onStart,enabled=state.loaded)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                FigmaAsset("f737d","a1319",14.dp)
                Text("今日已專注 ${minuteCount(todayFocus)} 分鐘",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            }
        }
    }
}

@Composable
fun ActiveScreen(active:ActiveSession,progress:SessionProgress?,apps:List<InstalledApp>,onLaunch:(String)->Unit,onExit:()->Unit) {
    if(progress==null) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        return
    }
    val resting=progress.phase in listOf(SessionPhase.SHORT_BREAK,SessionPhase.LONG_BREAK)
    val finalRest=progress.phase==SessionPhase.LONG_BREAK&&progress.round>=progress.rounds
    val phaseMinutes=when(progress.phase) {
        SessionPhase.FOCUS->active.plan.focusMinutes
        SessionPhase.SHORT_BREAK->active.plan.shortBreakMinutes
        SessionPhase.LONG_BREAK->active.plan.longBreakMinutes
        SessionPhase.FINISHED->0
    }
    val remainingFraction=(progress.remainingMillis.toFloat()/(phaseMinutes*60_000L).coerceAtLeast(1)).coerceIn(0f,1f)
    val caption=when {
        progress.phase==SessionPhase.LONG_BREAK->"長休息 · 第 ${progress.round} / ${progress.rounds} 輪"
        resting->"短休息 · 第 ${progress.round} / ${progress.rounds} 輪"
        active.plan.mode==FocusMode.POMODORO->"番茄鐘 · 第 ${progress.round} / ${progress.rounds} 輪"
        else->"單次專注 · ${active.plan.focusMinutes} 分鐘"
    }
    Page {
        Stack {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(if(resting)"休息一下" else "專注中",style=MaterialTheme.typography.headlineMedium)
                    Text(caption,style=MaterialTheme.typography.bodyMedium,color=palette.muted)
                }
                Badge("白名單生效")
            }
            Spacer(Modifier.height(16.dp))
            TimerFace(progress.remainingMillis,if(resting)"離開螢幕，休息一下" else "保持專注",if(resting)caption else "剩餘時間",
                remainingFraction=remainingFraction)
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(resting)"起身走走，讓眼睛休息。" else "慢慢來，一次只做一件事。",style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
                Text(if(finalRest)"即將完成本次專注，讓自己好好休息。" else if(resting)"下一輪將自動開始，不必守著螢幕。" else "這段時間，留給你真正想完成的事。",
                    style=MaterialTheme.typography.bodySmall,color=palette.muted,textAlign=TextAlign.Center)
            }
        }
        Stack(gap=12.dp) {
            Spacer(Modifier.height(32.dp))
            if(resting) Notice("休息中，白名單仍生效","這段休息也留給自己，先讓手機安靜一下。")
            else Text("允許使用的 App",style=MaterialTheme.typography.labelMedium,color=palette.muted)
            AllowedApps(apps.filter {it.packageName in active.plan.allowedPackages || it.essential},onLaunch)
            ActionButton("提前結束",onExit,ghost=true)
        }
    }
}

@Composable
fun ExitReasonScreen(reason:String,onReason:(String)->Unit,onContinue:()->Unit,onRequest:()->Unit) {
    Page {
        Stack {
            PageHeader("這次，為什麼需要離開？","先寫下原因，了解自己分心的時刻。",onContinue)
            Notice("專注限制仍持續生效","填寫原因後，還需要等待 60 秒才能確認結束。",warning=true)
            SheetCard {
                Text("退出原因",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                TextField(value=reason,onValueChange={onReason(it.take(100))},
                    modifier=Modifier.fillMaxWidth().heightIn(min=120.dp),placeholder={Text("寫下這次需要離開的原因…")},
                    colors=TextFieldDefaults.colors(focusedContainerColor=palette.surface,unfocusedContainerColor=palette.surface,
                        focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent),
                    supportingText={Text("原因為必填　${reason.length} / 100")})
            }
            Text("中途結束會保留已專注的時間，\n這次紀錄會標示為「提前退出」。",style=MaterialTheme.typography.bodyMedium,color=palette.muted)
        }
        Stack {
            Spacer(Modifier.height(24.dp))
            ActionButton("下一步，等待 60 秒",onRequest,warning=true,enabled=reason.isNotBlank())
            ActionButton("繼續專注",onContinue,secondary=true)
        }
    }
}

@Composable
fun ExitWaitScreen(active:ActiveSession,progress:SessionProgress?,onContinue:()->Unit,onConfirm:()->Unit) {
    val remaining=progress?.exitRemainingMillis ?: 60_000L
    val ready=remaining<=0
    Page {
        Stack {
            PageHeader(if(ready)"想好了，再做決定。" else "先等一下，再決定。",
                if(ready)"你可以繼續專注，或確認提前結束。" else "等滿 60 秒後，才能確認結束這次專注。")
            Spacer(Modifier.height(8.dp))
            TimerFace(remaining,if(ready)"已留給自己一點時間" else "給自己一點時間",
                if(ready)"現在可以確認退出" else "等待結束後可確認退出",warning=true)
            Spacer(Modifier.height(8.dp))
            SheetCard {
                Text("退出原因",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                Spacer(Modifier.height(4.dp));Text(active.exitReason,style=MaterialTheme.typography.bodyMedium)
            }
            Text("原本的專注計時仍在繼續。\n若專注先到期，將正常記錄為完成。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
        }
        Stack {
            Spacer(Modifier.height(24.dp))
            ActionButton("繼續專注",onContinue)
            ActionButton(if(ready)"確認提前結束" else "等待結束後可確認退出",onConfirm,enabled=ready,warning=true)
        }
    }
}

@Composable
fun ResultScreen(result:FocusHistory,state:FocusUiState,onHome:()->Unit,onHistory:()->Unit) {
    val complete=result.outcome==SessionOutcome.COMPLETED
    val today=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todayFocus=state.history.filter {it.startedAtMillis>=today}.sumOf {it.focusedMillis}
    Page {
        Stack {
            Brand();Spacer(Modifier.height(40.dp))
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(if(complete)palette.soft else palette.warningSoft),contentAlignment=Alignment.Center) {
                    if(complete) FigmaAsset("86470",size=44.dp)
                    else Text(if(result.outcome==SessionOutcome.INTERRUPTED)"!" else "↗",fontSize=36.sp,color=palette.warning)
                }
            }
            PageHeader(if(complete)"這 ${minuteCount(result.focusedMillis)} 分鐘，\n留給了自己。" else if(result.outcome==SessionOutcome.EXITED)"每一次回到眼前，\n都是一點累積。" else "專注已中斷，\n紀錄仍為你保留。")
            Text(if(complete)"每一次回到眼前，都是一點累積。" else if(result.outcome==SessionOutcome.EXITED)"這次紀錄已標示為提前退出。" else result.interruptionReason,
                style=MaterialTheme.typography.bodyMedium,color=palette.muted)
            SheetCard(padding=20.dp) {
                ResultRow("本次專注",durationLabel(result.focusedMillis))
                ResultRow("今日累計",durationLabel(todayFocus))
                ResultRow("擋下分心","${state.blocks.count {it.sessionId==result.id}} 次")
                if(result.mode==FocusMode.POMODORO) ResultRow("完成輪數","${result.completedRounds} 輪")
                if(result.exitReason.isNotBlank()) Text("退出原因：${result.exitReason}",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            }
        }
        Stack {
            Spacer(Modifier.height(24.dp))
            ActionButton("完成，回到首頁",onHome)
            ActionButton("看看專注紀錄",onHistory,secondary=true)
        }
    }
}

@Composable
private fun ResultRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=palette.muted)
        Text(value,style=MaterialTheme.typography.titleLarge,color=palette.primary,fontWeight=FontWeight.Bold)
    }
}
