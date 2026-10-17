package com.ray.phnomy.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.caverock.androidsvg.SVG
import com.ray.phnomy.R
import com.ray.phnomy.core.ActiveSession
import com.ray.phnomy.core.SessionPhase
import com.ray.phnomy.core.SessionProgress
import java.util.Locale

/** Figma's real typefaces, vector ring, and theme, including narrow split panes. */
internal class ProtectionOverlay(
    private val context: Context,
    active: ActiveSession,
    blockedPackage: String,
    dark: Boolean,
    compact: Boolean,
    onFocus: () -> Unit,
    onExit: () -> Unit,
    onHome: () -> Unit,
    onShortcut: (String) -> Unit,
) {
    private val palette = if (dark) Colors(0xFF121C17.toInt(), 0xFFECF1E8.toInt(), 0xFFB4C1B6.toInt(),
        0xFFACCCB4.toInt(), 0xFF121C17.toInt(), 0xFF2A3D30.toInt())
    else Colors(0xFFF7F7F2.toInt(), 0xFF202A24.toInt(), 0xFF626F66.toInt(),
        0xFF274C3D.toInt(), Color.WHITE, 0xFFE9F0E7.toInt())
    private val bodyFont = context.resources.getFont(R.font.noto_sans_tc)
    private val monoFont = context.resources.getFont(R.font.roboto_mono)
    val view = ScrollView(context).apply { setBackgroundColor(palette.background); isFillViewport = true }
    private val timer = text("", if (compact) 44f else 64f, palette.primary).apply {
        typeface = monoFont
        fontVariationSettings = "'wght' 450"
    }
    private val hint = text("", 14f, palette.muted)

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val horizontal = if (compact) 16 else 24
            setPadding(dp(horizontal), dp(28), dp(horizontal), dp(24))
        }
        view.addView(column, FrameLayout.LayoutParams(-1, -1))
        column.addView(text("P H N O M Y", 14f, palette.primary).apply { letterSpacing = .14f }, lp(bottom = 24))
        column.addView(VectorRing(context, dark), LinearLayout.LayoutParams(dp(if (compact) 64 else 88), dp(if (compact) 64 else 88)).apply { bottomMargin = dp(20) })
        column.addView(text("把注意力，留給現在", if (compact) 22f else 28f, palette.text, true), lp(bottom = 12))
        val label = appLabel(blockedPackage)
        column.addView(text("$label 不在本次白名單中\n這一刻，讓自己回到正在做的事。", 15f, palette.muted).apply { setLineSpacing(dp(5).toFloat(), 1f) }, lp(bottom = 24))
        column.addView(timer, lp(bottom = 4))
        column.addView(hint, lp(bottom = 24))
        column.addView(button("返回專注", true, onFocus), lp(bottom = 12, height = 56))
        column.addView(button("回到主畫面", false, onHome), lp(bottom = 12, height = 56))
        val dialer = runCatching { context.getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage }.getOrNull()
        val shortcuts = (active.plan.allowedPackages + listOfNotNull(dialer))
            .filter { it != context.packageName && context.packageManager.getLaunchIntentForPackage(it) != null }.take(4)
        if (shortcuts.isNotEmpty()) {
            column.addView(text("必要時，使用允許的 App", 13f, palette.muted), lp(top = 8, bottom = 8))
            shortcuts.forEach { pkg -> column.addView(button(appLabel(pkg), false) { onShortcut(pkg) }, lp(bottom = 8, height = 48)) }
        }
        column.addView(button("需要提前結束", false, onExit), lp(top = 8, height = 48))
        column.addView(text("仍可從系統設定關閉保護。", 12f, palette.muted), lp(top = 8))
    }

    fun update(progress: SessionProgress) {
        val seconds = (progress.remainingMillis.coerceAtLeast(0) + 999) / 1_000
        timer.text = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
        hint.text = if (progress.phase == SessionPhase.FOCUS) "專注進行中 · 第 ${progress.round} / ${progress.rounds} 輪"
            else "休息進行中 · 白名單仍生效"
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String) = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("這個 App")

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(color)
        typeface = if (bold) Typeface.create(bodyFont, Typeface.BOLD) else bodyFont
        fontVariationSettings = "'wght' ${if (bold) 750 else 450}"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun button(value: String, filled: Boolean, onClick: () -> Unit) = Button(context).apply {
        text = value; textSize = 15f; isAllCaps = false; minHeight = dp(48); typeface = bodyFont
        fontVariationSettings = "'wght' 550"
        setTextColor(if (filled) palette.onPrimary else palette.primary)
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(if (filled) palette.primary else palette.soft) }
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0, bottom: Int = 0, height: Int = -2) = LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private data class Colors(val background: Int, val text: Int, val muted: Int, val primary: Int, val onPrimary: Int, val soft: Int)

    private class VectorRing(context: Context, dark: Boolean) : View(context) {
        private val picture = SVG.getFromAsset(context.assets, "figma/${if (dark) "89904" else "f9714"}.svg").renderToPicture()
        override fun onDraw(canvas: Canvas) { canvas.drawPicture(picture, RectF(0f, 0f, width.toFloat(), height.toFloat())) }
    }
}
