package com.ray.phnomy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ray.phnomy.R
import com.ray.phnomy.core.ThemeMode

@OptIn(ExperimentalTextApi::class)
val NotoSansTc = FontFamily(
    Font(R.font.noto_sans_tc,weight=FontWeight.Normal,
        variationSettings=FontVariation.Settings(FontVariation.weight(450))),
    Font(R.font.noto_sans_tc,weight=FontWeight.Medium,
        variationSettings=FontVariation.Settings(FontVariation.weight(550))),
    Font(R.font.noto_sans_tc,weight=FontWeight.Bold,
        variationSettings=FontVariation.Settings(FontVariation.weight(750))),
)
@OptIn(ExperimentalTextApi::class)
val RobotoMono = FontFamily(Font(R.font.roboto_mono,weight=FontWeight.Normal,
    variationSettings=FontVariation.Settings(FontVariation.weight(450))))

data class PhnomyPalette(val background: Color, val surface: Color, val text: Color,
    val muted: Color, val primary: Color, val onPrimary: Color, val soft: Color,
    val line: Color, val warning: Color, val warningSoft: Color, val dark: Boolean)

private val Light = PhnomyPalette(Color(0xFFF7F7F2), Color.White, Color(0xFF202A24),
    Color(0xFF626F66), Color(0xFF274C3D), Color.White, Color(0xFFE9F0E7),
    Color(0xFFDFE5DC), Color(0xFF92592B), Color(0xFFF5EBDD), false)
private val Dark = PhnomyPalette(Color(0xFF121C17), Color(0xFF1B2921), Color(0xFFECF1E8),
    Color(0xFFB4C1B6), Color(0xFFACCCB4), Color(0xFF121C17), Color(0xFF2A3D30),
    Color(0xFF3B5041), Color(0xFFE8B98C), Color(0xFF3C2E21), true)
val LocalPhnomyPalette = staticCompositionLocalOf { Light }
val palette: PhnomyPalette @Composable get() = LocalPhnomyPalette.current

@Composable
fun PhnomyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when(mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val p = if(dark) Dark else Light
    val colors = if(dark) darkColorScheme(primary=p.primary, onPrimary=p.onPrimary,
        primaryContainer=p.soft, onPrimaryContainer=p.primary, background=p.background,
        onBackground=p.text, surface=p.surface, onSurface=p.text, surfaceVariant=p.soft,
        onSurfaceVariant=p.muted, outline=p.line, error=p.warning)
    else lightColorScheme(primary=p.primary, onPrimary=p.onPrimary, primaryContainer=p.soft,
        onPrimaryContainer=p.primary, background=p.background, onBackground=p.text,
        surface=p.surface, onSurface=p.text, surfaceVariant=p.soft, onSurfaceVariant=p.muted,
        outline=p.line, error=p.warning)
    fun type(size:Int, weight:FontWeight=FontWeight.Normal) = TextStyle(fontFamily=NotoSansTc,
        fontWeight=weight,fontSize=size.sp,lineHeight=(size*1.5).sp,letterSpacing=0.sp)
    val typography = Typography(displayLarge=type(48), displayMedium=type(40), displaySmall=type(32),
        headlineLarge=type(32,FontWeight.Bold),headlineMedium=type(28,FontWeight.Bold),
        headlineSmall=type(24,FontWeight.Bold),titleLarge=type(20,FontWeight.Medium),
        titleMedium=type(16,FontWeight.Medium),titleSmall=type(14,FontWeight.Medium),
        bodyLarge=type(16),bodyMedium=type(14),bodySmall=type(12),
        labelLarge=type(16,FontWeight.Medium),labelMedium=type(12,FontWeight.Medium),labelSmall=type(11))
    CompositionLocalProvider(LocalPhnomyPalette provides p) { MaterialTheme(colorScheme=colors,typography=typography,content=content) }
}
