package com.example.lockscreenapps

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.graphics.ColorUtils

const val MATCH = -1
const val WRAP = -2

object Look {
    val BG_NAMES = arrayOf("Slate", "Ocean", "Forest", "Sunset", "Violet", "Rose", "Graphite", "Midnight")
    val BG_COLORS = intArrayOf(
        Color.parseColor("#334155"), Color.parseColor("#0E4A6B"),
        Color.parseColor("#1E5B3A"), Color.parseColor("#8A3B12"),
        Color.parseColor("#4B2A7B"), Color.parseColor("#8A2846"),
        Color.parseColor("#2B2B2B"), Color.parseColor("#0B1026")
    )
    val ACCENTS = intArrayOf(
        Color.parseColor("#3B82F6"), Color.parseColor("#10B981"),
        Color.parseColor("#F59E0B"), Color.parseColor("#EF4444"),
        Color.parseColor("#A855F7"), Color.parseColor("#EC4899")
    )
}

class Palette(cfg: Cfg, systemDark: Boolean) {
    val dark: Boolean = when (cfg.theme) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val accent: Int = if (cfg.accentIdx in Look.ACCENTS.indices) {
        Look.ACCENTS[cfg.accentIdx]
    } else {
        Color.HSVToColor(floatArrayOf(cfg.accentHue.toFloat(), 0.75f, 1f))
    }
    val onAccent: Int = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
    val shadows: Boolean = !cfg.lowPower && !cfg.highContrast

    private val solid = cfg.reduceTransparency || cfg.highContrast

    val windowColor: Int
    val text: Int
    val sub: Int
    val card: Int
    val stroke: Int

    init {
        val base = Look.BG_COLORS[cfg.bg.coerceIn(0, Look.BG_COLORS.size - 1)]
        val tinted = when {
            cfg.highContrast -> if (dark) Color.BLACK else Color.WHITE
            dark -> base
            else -> mix(base, Color.WHITE, 0.82f)
        }
        // Never go below 40% so text stays readable over whatever is behind the panel
        val alpha = if (solid) 255 else cfg.opacity.coerceIn(40, 100) * 255 / 100
        windowColor = Color.argb(alpha, Color.red(tinted), Color.green(tinted), Color.blue(tinted))

        text = if (dark) Color.WHITE else Color.parseColor("#101418")
        sub = if (dark) Color.parseColor("#CCFFFFFF") else Color.parseColor("#B3101418")
        card = when {
            cfg.highContrast -> if (dark) Color.parseColor("#1A1A1A") else Color.parseColor("#F2F2F2")
            solid -> if (dark) Color.parseColor("#262B33") else Color.parseColor("#ECEFF3")
            dark -> Color.parseColor("#26FFFFFF")
            else -> Color.parseColor("#A6FFFFFF")
        }
        stroke = when {
            cfg.highContrast -> text
            dark -> Color.parseColor("#40FFFFFF")
            else -> Color.parseColor("#33000000")
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )
}

class Glass(val ctx: Context, val p: Palette) {
    val dp: Float = ctx.resources.displayMetrics.density

    fun px(v: Int): Int = (v * dp).toInt()

    fun roundRect(color: Int, radiusDp: Int, strokeColor: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = radiusDp * dp
        d.setColor(color)
        d.setStroke(px(1), strokeColor)
        return d
    }

    fun card(vararg kids: View): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = roundRect(p.card, 20, p.stroke)
        c.setPadding(px(16), px(12), px(16), px(12))
        if (p.shadows) c.elevation = px(3).toFloat()
        val params = LinearLayout.LayoutParams(MATCH, WRAP)
        params.setMargins(0, px(8), 0, px(8))
        c.layoutParams = params
        for (k in kids) c.addView(k)
        return c
    }

    fun title(t: String): TextView {
        val v = TextView(ctx)
        v.text = t
        v.setTextColor(p.text)
        v.textSize = 20f
        v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    fun label(t: String): TextView {
        val v = TextView(ctx)
        v.text = t
        v.setTextColor(p.text)
        v.textSize = 15f
        v.setPadding(0, px(10), 0, px(2))
        return v
    }

    fun sub(t: String): TextView {
        val v = TextView(ctx)
        v.text = t
        v.setTextColor(p.sub)
        v.textSize = 12f
        return v
    }

    fun switchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, px(8), 0, px(8))

        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val name = TextView(ctx)
        name.text = title
        name.setTextColor(p.text)
        name.textSize = 15f
        texts.addView(name)
        if (hint != null) texts.addView(sub(hint))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val sw = Switch(ctx)
        sw.isChecked = checked
        sw.thumbTintList = ColorStateList.valueOf(p.accent)
        sw.trackTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(p.accent, 110))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw)
        return row
    }

    fun slider(
        title: String, lo: Int, hi: Int, value: Int,
        fmt: (Int) -> String, onChange: (Int) -> Unit, onDone: (Int) -> Unit
    ): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, px(6), 0, px(6))

        val head = LinearLayout(ctx)
        val name = TextView(ctx)
        name.text = title
        name.setTextColor(p.text)
        name.textSize = 15f
        val shown = TextView(ctx)
        shown.text = fmt(value)
        shown.setTextColor(p.sub)
        shown.textSize = 13f
        head.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(shown)
        box.addView(head)

        val bar = SeekBar(ctx)
        bar.max = hi - lo
        bar.progress = value - lo
        bar.progressTintList = ColorStateList.valueOf(p.accent)
        bar.thumbTintList = ColorStateList.valueOf(p.accent)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                shown.text = fmt(v + lo)
                if (fromUser) onChange(v + lo)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                onDone((s?.progress ?: 0) + lo)
            }
        })
        box.addView(bar)
        return box
    }

    fun chips(options: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(ctx)
        row.setPadding(0, px(4), 0, px(4))
        options.forEachIndexed { i, name ->
            val on = i == selected
            val tv = TextView(ctx)
            tv.text = name
            tv.gravity = Gravity.CENTER
            tv.textSize = 13f
            tv.setPadding(px(6), px(10), px(6), px(10))
            tv.setTextColor(if (on) p.onAccent else p.text)
            tv.background = roundRect(if (on) p.accent else Color.TRANSPARENT, 14, if (on) p.accent else p.stroke)
            tv.setOnClickListener { onPick(i) }
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(px(3), 0, px(3), 0)
            row.addView(tv, lp)
        }
        return row
    }

    fun swatches(colors: IntArray, selected: Int, onPick: (Int) -> Unit): View {
        val wrap = LinearLayout(ctx)
        wrap.orientation = LinearLayout.VERTICAL
        val rows = mutableListOf<LinearLayout>()
        colors.forEachIndexed { i, col ->
            if (i % 4 == 0) {
                val r = LinearLayout(ctx)
                rows.add(r)
                wrap.addView(r)
            }
            val dot = View(ctx)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(col)
            d.setStroke(px(if (i == selected) 3 else 1), if (i == selected) p.text else p.stroke)
            dot.background = d
            dot.setOnClickListener { onPick(i) }
            val lp = LinearLayout.LayoutParams(px(44), px(44))
            lp.setMargins(px(4), px(4), px(4), px(4))
            rows.last().addView(dot, lp)
        }
        return wrap
    }

    fun button(t: String, filled: Boolean, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(if (filled) p.onAccent else p.text)
        b.background = roundRect(if (filled) p.accent else Color.TRANSPARENT, 14, if (filled) p.accent else p.stroke)
        b.setOnClickListener { onClick() }
        return b
    }
}

abstract class GlassActivity : Activity() {
    lateinit var cfg: Cfg
    lateinit var pal: Palette
    lateinit var g: Glass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        build()
    }

    // Re-reads every saved setting and re-applies the window look
    protected fun load() {
        cfg = Cfg(this)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        pal = Palette(cfg, night)
        g = Glass(this, pal)
        applyWindow()
    }

    private fun applyWindow() {
        window.setBackgroundDrawable(ColorDrawable(pal.windowColor))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 31) {
            val on = !cfg.noBlur && windowManager.isCrossWindowBlurEnabled
            window.setBackgroundBlurRadius(if (on) (cfg.blur * g.dp * 0.8f).toInt() else 0)
        }
        val flags = window.decorView.systemUiVisibility
        window.decorView.systemUiVisibility = if (pal.dark) {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    abstract fun build()
}
