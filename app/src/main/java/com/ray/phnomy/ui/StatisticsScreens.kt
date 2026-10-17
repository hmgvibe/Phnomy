package com.ray.phnomy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ray.phnomy.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val zone:ZoneId get()=ZoneId.systemDefault()
private fun dateOf(millis:Long)=Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
private fun timeOf(millis:Long)=Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun StatisticsScreen(state:FocusUiState,days:Int,onDays:(Int)->Unit,onHistory:()->Unit,onUsage:()->Unit) {
    val today=LocalDate.now()
    val first=today.minusDays((days-1).toLong())
    val interval=state.history.filter { dateOf(it.startedAtMillis) in first..today }
    val total=interval.sumOf {it.focusedMillis}
    val completed=interval.count {it.outcome==SessionOutcome.COMPLETED}
    val percent=if(interval.isEmpty())null else (completed*100f/interval.size).roundToInt()
    val blocks=state.blocks.count {dateOf(it.atMillis) in first..today}
    val usage=state.usage
    val hasUsage=state.permissions.usage&&usage?.permissionGranted==true
    val todayFocus=state.history.filter {dateOf(it.startedAtMillis)==today}.sumOf {it.focusedMillis}
    Page {
        Stack(gap=12.dp) {
            PageHeader("看見累積的改變","看見每一次把注意力帶回來的努力")
            TwoChoice(listOf("今日","近七日"),if(days==1)0 else 1){onDays(if(it==0)1 else 7)}
            SheetCard {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(days==1)"今日專注" else "近七日專注",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=palette.muted)
                    Text(durationLabel(total),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                val bars=if(days==7) (6 downTo 0).map { offset ->
                    val day=today.minusDays(offset.toLong())
                    BarValue(day.dayOfMonth.toString(),interval.filter {dateOf(it.startedAtMillis)==day}.sumOf {it.focusedMillis},day==today)
                } else interval.sortedBy {it.startedAtMillis}.mapIndexed {index,item ->BarValue(timeOf(item.startedAtMillis),item.focusedMillis,index==interval.lastIndex)}
                FocusBars(bars)
                Spacer(Modifier.height(12.dp))
                Text(if(days==1)"${today.monthValue} 月 ${today.dayOfMonth} 日" else "${first.monthValue} 月 ${first.dayOfMonth} 日 — ${today.dayOfMonth} 日",
                    style=MaterialTheme.typography.bodySmall,color=palette.muted)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.soft).padding(16.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("完成率 ${percent?.let {"$it%"} ?: "—"}",style=MaterialTheme.typography.titleSmall,color=palette.primary)
                    Text("$completed / ${interval.size} 次專注",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                }
                Column(Modifier.weight(1f)) {
                    Text("攔截 $blocks 次",style=MaterialTheme.typography.titleSmall,color=palette.primary)
                    Text("把注意力帶回此刻",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MetricCard("今日專注",minuteCount(todayFocus).toString(),"分鐘",Modifier.weight(1f))
                MetricCard(if(days==1)"今日 App 使用合計" else "近七日 App 使用合計",if(hasUsage)minuteCount(usage!!.totalMillis).toString() else "—",if(hasUsage)"分鐘" else if(state.permissions.usage&&usage==null)"讀取中" else "未授權",Modifier.weight(1f))
            }
            Text("系統每日彙總，可能含分割畫面重疊。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
            if(interval.isEmpty()) Notice("從第一次專注開始","完成一段專注後，你的累積會出現在這裡。")
            SheetCard {
                Text(if(days==1)"今日 App 使用時間" else "近七日 App 使用時間",style=MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                when {
                    !state.permissions.usage -> {
                        Text("未授予使用情況存取。專注紀錄仍正常保存。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                        TextButton(onClick=onUsage){Text("前往開啟使用情況存取")}
                    }
                    usage==null -> Text("讀取使用時間中…",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                    usage.apps.isEmpty() -> Text("這段時間沒有可顯示的 App 使用資料。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
                    else -> usage.apps.sortedByDescending {it.foregroundMillis}.forEach { app -> UsageRow(app,usage.totalMillis) }
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("僅儲存於本機",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=palette.muted)
                TextButton(onClick=onHistory) { Text("歷史紀錄"); Spacer(Modifier.width(4.dp));FigmaAsset("3d1dc","c89ec",16.dp) }
            }
        }
    }
}

private data class BarValue(val label:String,val millis:Long,val highlighted:Boolean)

@Composable
private fun FocusBars(values:List<BarValue>) {
    val p=palette
    if(values.isEmpty()) {
        Box(Modifier.fillMaxWidth().heightIn(min=104.dp),contentAlignment=Alignment.Center) {
            Text("今天還沒有專注紀錄",style=MaterialTheme.typography.bodySmall,color=p.muted)
        }
        return
    }
    val max=values.maxOf {it.millis}.coerceAtLeast(1)
    val scroll=values.size>7
    Row(if(scroll)Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val column=if(scroll)Modifier.width(52.dp) else Modifier.weight(1f)
            Column(column.semantics {contentDescription="${value.label}，專注 ${minuteCount(value.millis)} 分鐘"},
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(84.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                    Box(Modifier.width(if(values.size<=2)64.dp else 24.dp).height((if(value.millis==0L)2f else (value.millis.toFloat()/max*70).coerceAtLeast(3f)).dp)
                        .clip(RoundedCornerShape(6.dp)).background(if(value.highlighted)p.primary else p.soft))
                }
                Text(value.label,style=MaterialTheme.typography.labelSmall,color=if(value.highlighted)p.primary else p.muted,
                    textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MetricCard(title:String,value:String,unit:String,modifier:Modifier) {
    Column(modifier.heightIn(min=92.dp).clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.bodySmall,color=palette.muted)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(value,fontFamily=RobotoMono,fontSize=28.sp,lineHeight=34.sp,color=palette.text)
            Text(unit,style=MaterialTheme.typography.bodySmall,color=palette.muted)
        }
    }
}

@Composable
private fun UsageRow(app:AppUsage,total:Long) {
    val ratio=(app.foregroundMillis.toFloat()/total.coerceAtLeast(1)).coerceIn(0f,1f)
    Row(Modifier.fillMaxWidth().heightIn(min=26.dp).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(app.label,Modifier.width(80.dp),style=MaterialTheme.typography.bodySmall,color=palette.muted,maxLines=2,overflow=TextOverflow.Ellipsis)
        LinearProgressIndicator(progress={ratio},modifier=Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color=palette.primary,trackColor=palette.soft)
        Text("${minuteCount(app.foregroundMillis)} 分",Modifier.width(60.dp),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HistoryScreen(state:FocusUiState,onBack:()->Unit) {
    val history=state.history.sortedByDescending {it.startedAtMillis}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(top=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {PageHeader("每一次專注，都算數","歷史紀錄",onBack)}
        item {
            Notice("已完成 ${history.count {it.outcome==SessionOutcome.COMPLETED}} 次","累積專注 ${durationLabel(history.sumOf {it.focusedMillis})}，每一次回到眼前都是累積。")
        }
        if(history.isEmpty()) item {Notice("還沒有紀錄","開始第一段專注，為自己留下時間。")}
        items(history,key={it.id}) { record ->
            SheetCard {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(when(record.outcome){SessionOutcome.COMPLETED->if(record.mode==FocusMode.POMODORO)"番茄鐘" else "單次專注";SessionOutcome.EXITED->"提前結束";SessionOutcome.INTERRUPTED->"專注中斷"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Badge(when(record.outcome){SessionOutcome.COMPLETED->"已完成";SessionOutcome.EXITED->"提前退出";SessionOutcome.INTERRUPTED->"已中斷"},warning=record.outcome!=SessionOutcome.COMPLETED)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    val date=dateOf(record.startedAtMillis)
                    Text("${date.monthValue} 月 ${date.dayOfMonth} 日・${timeOf(record.startedAtMillis)}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=palette.muted)
                    Text(durationLabel(record.focusedMillis),style=MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(when(record.outcome) {
                    SessionOutcome.COMPLETED->if(record.mode==FocusMode.POMODORO)"完成 ${record.completedRounds} 輪，為自己留下安靜。" else "有始有終，為自己留下一段安靜。"
                    SessionOutcome.EXITED->"退出原因：${record.exitReason}"
                    SessionOutcome.INTERRUPTED->record.interruptionReason
                },style=MaterialTheme.typography.bodySmall,color=palette.muted)
            }
        }
        item {Text("僅儲存於本機",style=MaterialTheme.typography.bodySmall,color=palette.muted)}
    }
}
