package com.ray.phnomy.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import com.ray.phnomy.core.InstalledApp
import kotlin.math.ceil

/** Original, unmodified Figma SVGs. Their intrinsic viewports are preserved. */
@Composable
fun FigmaAsset(light: String, dark: String = light, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = if(palette.dark) dark else light
    val picture = remember(file) { SVG.getFromAsset(context.assets,"figma/$file.svg").renderToPicture() }
    Canvas(modifier.size(size)) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(this.size.width / picture.width, this.size.height / picture.height)
            native.drawPicture(picture)
            native.restore()
        }
    }
}

@Composable
fun Brand() {
    Row(Modifier.fillMaxWidth().heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically) {
        FigmaAsset("3e6de","b2ef7",24.dp)
        Spacer(Modifier.width(8.dp))
        Text("Phnomy",style=MaterialTheme.typography.titleLarge,color=palette.primary)
        Spacer(Modifier.weight(1f))
        Badge("完整離線")
    }
}

@Composable
fun Badge(text:String, warning:Boolean=false) {
    val p=palette
    Text(text,Modifier.clip(RoundedCornerShape(99.dp)).background(if(warning)p.warningSoft else p.soft)
        .padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,
        color=if(warning)p.warning else p.primary)
}

@Composable
fun Page(content:@Composable ColumnScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        val viewport=maxHeight
        Column(Modifier.widthIn(max=520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min=viewport).padding(horizontal=24.dp,vertical=16.dp),
            verticalArrangement=Arrangement.SpaceBetween,content=content)
    }
}

@Composable
fun Stack(gap:Dp=16.dp,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(gap),content=content)
}

@Composable
fun PageHeader(title:String, subtitle:String?=null, back:(()->Unit)?=null) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(back!=null) {
            Row(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role=Role.Button,onClick=back).padding(end=16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FigmaAsset("d9401",size=20.dp)
                Text("返回",color=palette.muted,style=MaterialTheme.typography.bodyMedium)
            }
        }
        Text(title,style=MaterialTheme.typography.headlineMedium,color=palette.text)
        if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=palette.muted)
    }
}

@Composable
fun ActionButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    secondary:Boolean=false,warning:Boolean=false,ghost:Boolean=false) {
    val p=palette
    val container=when { ghost->Color.Transparent; warning->p.warningSoft; secondary->p.soft; else->p.primary }
    val foreground=when { warning->p.warning; secondary||ghost->p.primary; else->p.onPrimary }
    Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=56.dp),
        shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=container,
            contentColor=foreground,disabledContainerColor=p.line,disabledContentColor=p.muted),
        contentPadding=PaddingValues(horizontal=16.dp,vertical=14.dp)) {
        Text(text,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center)
    }
}

@Composable
fun Notice(title:String,body:String,warning:Boolean=false,icon:Boolean=false) {
    val p=palette
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if(warning)p.warningSoft else p.soft).padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(icon) { FigmaAsset("3f205",size=24.dp); Spacer(Modifier.height(4.dp)) }
        Text(title,style=MaterialTheme.typography.titleSmall,color=if(warning)p.warning else p.primary)
        Text(body,style=MaterialTheme.typography.bodySmall,color=p.muted)
    }
}

@Composable
fun SheetCard(modifier:Modifier=Modifier,padding:Dp=16.dp,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(padding),content=content)
}

@Composable
fun TwoChoice(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.line).padding(4.dp),
        horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index,label ->
            Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(10.dp))
                .background(if(index==selected)palette.surface else Color.Transparent)
                .clickable(role=Role.Tab){onSelect(index)}.padding(horizontal=4.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                Text(label,style=MaterialTheme.typography.titleSmall,color=if(index==selected)palette.primary else palette.muted,textAlign=TextAlign.Center)
            }
        }
    }
}

@Composable
fun BottomTabs(selected:String,onSelect:(String)->Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf(Triple("home","專注",Pair("1bb75","3ad0d")),
            Triple("stats","統計",Pair("184ad","a4a19")), Triple("settings","設定",Pair("b50ee","6db49")))
            .forEach { (key,label,icons) ->
                val active=selected==key
                Column(Modifier.weight(1f).heightIn(min=64.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(role=Role.Tab){onSelect(key)}.padding(vertical=6.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterVertically)) {
                    Box(Modifier.size(56.dp,28.dp).clip(RoundedCornerShape(14.dp))
                        .background(if(active)palette.soft else Color.Transparent),contentAlignment=Alignment.Center) {
                        val light=if(active)icons.first else icons.second
                        val dark=when(key){"home"->if(active)"f1926" else "3afcd";"stats"->if(active)"36f83" else "f470d";else->"72c8f"}
                        FigmaAsset(light,dark,20.dp)
                    }
                    Text(label,style=MaterialTheme.typography.bodySmall,color=if(active)palette.primary else palette.muted)
                }
            }
    }
}

@Composable
fun SettingsRow(title:String,detail:String?=null,icon:String?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick)
        .padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(icon!=null) FigmaAsset(icon,size=20.dp)
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=palette.text)
        if(detail!=null) Text(detail,style=MaterialTheme.typography.bodySmall,color=palette.muted)
        FigmaAsset("bd78b","c89ec",16.dp)
    }
}

@Composable
fun AppIcon(app:InstalledApp,size:Dp=40.dp) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val pixels=with(density){size.roundToPx()}
    val bitmap=remember(app.packageName,pixels) {
        runCatching { val drawable=context.packageManager.getApplicationIcon(app.packageName)
            val image=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,pixels,pixels); drawable.draw(android.graphics.Canvas(image)); image.asImageBitmap()
        }.getOrNull()
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(palette.soft),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap,null,Modifier.size(size))
        else Text(app.label.take(1),color=palette.primary,style=MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AllowedApps(apps:List<InstalledApp>,onLaunch:(String)->Unit) {
    if(apps.isEmpty()) Text("必要的電話、緊急撥號與系統操作仍可使用。",style=MaterialTheme.typography.bodySmall,color=palette.muted)
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        apps.forEach { app ->
            Column(Modifier.width(76.dp).heightIn(min=76.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role=Role.Button){onLaunch(app.packageName)}.padding(vertical=4.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                AppIcon(app)
                Text(app.label,style=MaterialTheme.typography.bodySmall,color=palette.muted,maxLines=2,
                    overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            }
        }
    }
}

@Composable
fun TimerFace(millis:Long,label:String,caption:String,warning:Boolean=false,remainingFraction:Float=1f) {
    val p=palette
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val diameter=minOf(maxWidth,328.dp)
        val fontScale=LocalDensity.current.fontScale
        val digits=clockTime(millis)
        val base=minOf(diameter.value/328f*96f,(diameter.value-12f)/(digits.length*0.61f),96f)
        val safeSize=(base/(fontScale.coerceAtLeast(1f))).coerceAtLeast(24f)
        val fraction=remainingFraction.coerceIn(0f,1f)
        Box(Modifier.size(diameter).semantics {
            contentDescription="$label，$digits，$caption"
            progressBarRangeInfo=ProgressBarRangeInfo(fraction,0f..1f)
        },contentAlignment=Alignment.Center) {
            FigmaAsset(if(warning)"96a32" else "f9714",if(warning)"96a32" else "89904",diameter,
                Modifier.drawWithContent {
                    drawCircle(p.line,radius=size.minDimension*152f/328f,style=Stroke(width=size.minDimension*2.5f/328f))
                    if(fraction>0f) {
                        val sector=Path().apply {
                            moveTo(size.width/2f,size.height/2f)
                            arcTo(Rect(0f,0f,size.width,size.height),135f,270f*fraction,false)
                            close()
                        }
                        clipPath(sector) {this@drawWithContent.drawContent()}
                    }
                })
            Column(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,color=p.muted,style=MaterialTheme.typography.titleSmall,textAlign=TextAlign.Center)
                Text(digits,color=if(warning)p.warning else p.primary,fontFamily=RobotoMono,
                    fontSize=safeSize.sp,lineHeight=(safeSize*1.21f).sp,maxLines=1)
                Text(caption,color=p.muted,style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
            }
        }
    }
}

fun clockTime(millis:Long):String {
    val seconds=ceil(millis.coerceAtLeast(0)/1000.0).toLong()
    return "%02d:%02d".format(seconds/60,seconds%60)
}

fun minuteCount(millis:Long)=millis.coerceAtLeast(0)/60_000
fun durationLabel(millis:Long):String {
    val minutes=minuteCount(millis)
    return if(minutes>=60) "${minutes/60} 小時 ${minutes%60} 分" else "$minutes 分鐘"
}
