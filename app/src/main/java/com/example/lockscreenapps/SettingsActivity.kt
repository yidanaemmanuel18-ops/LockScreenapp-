package com.example.lockscreenapps

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class SettingsActivity : GlassActivity() {

    private lateinit var scrollRef: ScrollView
    private lateinit var previewBox: LinearLayout
    private var firstResume = true

    override fun onResume() {
        super.onResume()
        // Coming back from Android's own settings (badge access): refresh the status line
        if (firstResume) firstResume = false else restyle()
    }

    override fun onBackPressed() {
        goBack()
    }

    private fun goBack() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // Rebuilds the screen (new colors etc.) and keeps your scroll position
    private fun restyle() {
        val y = scrollRef.scrollY
        load()
        build()
        scrollRef.post { scrollRef.scrollY = y }
    }

    private fun section(title: String, vararg v: View): View = g.card(g.title(title), *v)

    private fun sw(title: String, hint: String?, key: String, def: Boolean, after: () -> Unit = {}): View =
        g.switchRow(title, hint, Prefs.bool(this, key, def)) {
            Prefs.setBool(this, key, it)
            after()
        }

    private fun speedText(v: Int): String {
        val s = (6000 - v * 55).coerceAtLeast(500) / 1000.0
        return "every " + String.format("%.1f", s) + " s"
    }

    private fun fillPreview() {
        previewBox.removeAllViews()
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val size = g.px(cfg.iconDp)
        val order = Panel.order(this).take(5)
        if (order.isEmpty()) {
            for (i in 0 until 5) {
                val dot = View(this)
                val d = GradientDrawable()
                d.shape = GradientDrawable.OVAL
                d.setColor(Look.ACCENTS[i])
                dot.background = d
                val lp = LinearLayout.LayoutParams(size, size)
                lp.setMargins(g.px(4), g.px(4), g.px(4), g.px(4))
                row.addView(dot, lp)
            }
        } else {
            for (pkg in order) {
                val icon = ImageView(this)
                icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
                val lp = LinearLayout.LayoutParams(size, size)
                lp.setMargins(g.px(4), g.px(4), g.px(4), g.px(4))
                row.addView(icon, lp)
            }
        }
        previewBox.addView(row)
        previewBox.addView(g.sub("Opacity ${cfg.opacity}%  \u2022  Blur ${cfg.blur}%  \u2022  Icons ${cfg.iconDp} dp"))
    }

    private fun dialogTheme(): Int =
        if (pal.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    private fun chooseHidden() {
        val pinned = Prefs.load(this)
        if (pinned.isEmpty()) {
            Toast.makeText(this, "Pick some apps first", Toast.LENGTH_SHORT).show()
            return
        }
        val names = pinned.map { Panel.label(this, it) }.toTypedArray()
        val hidden = Prefs.list(this, "badge_hidden").toMutableSet()
        val checked = BooleanArray(pinned.size) { pinned[it] in hidden }
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Hide badges for")
            .setMultiChoiceItems(names, checked) { _, i, on ->
                if (on) hidden.add(pinned[i]) else hidden.remove(pinned[i])
            }
            .setPositiveButton("Save") { _, _ ->
                Prefs.setList(this, "badge_hidden", hidden.toList())
                LockScreenService.refresh(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun build() {
        val scroll = ScrollView(this)
        scrollRef = scroll
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(g.px(16), g.px(12), g.px(16), g.px(32))
        scroll.addView(col)

        // Header
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(g.button("\u2039 Back", false) { goBack() })
        val titleLp = LinearLayout.LayoutParams(0, WRAP, 1f)
        titleLp.setMargins(g.px(12), 0, 0, 0)
        header.addView(g.title("Settings"), titleLp)
        col.addView(header)

        // Live preview
        previewBox = LinearLayout(this)
        previewBox.orientation = LinearLayout.VERTICAL
        col.addView(section("Preview", previewBox))
        fillPreview()

        // Appearance
        col.addView(section("Appearance",
            g.label("Theme"),
            g.chips(listOf("System", "Light", "Dark"), cfg.theme) {
                Prefs.setInt(this, "theme", it)
                restyle()
            },
            g.label("Background color"),
            g.swatches(Look.BG_COLORS, cfg.bg) {
                Prefs.setInt(this, "bg", it)
                restyle()
            },
            g.label("Accent color"),
            g.swatches(Look.ACCENTS, cfg.accentIdx) {
                Prefs.setInt(this, "accent", it)
                restyle()
            },
            g.slider("Custom accent (hue)", 0, 360, cfg.accentHue, { "$it\u00B0" },
                { Prefs.setInt(this, "hue", it); Prefs.setInt(this, "accent", 6) },
                { restyle() }),
            g.slider("Opacity", 40, 100, cfg.opacity, { "$it%" },
                { Prefs.setInt(this, "opacity", it); load() },
                { fillPreview() }),
            g.slider("Blur intensity", 0, 100, cfg.blur, { "$it%" },
                { Prefs.setInt(this, "blur", it); load() },
                { fillPreview() }),
            g.sub("Blur needs Android 12 or newer. On older phones only transparency is used.")
        ))

        // Notification panel
        col.addView(section("Notification panel",
            g.label("Animation style"),
            g.chips(listOf("Off", "Carousel", "Pages"), cfg.anim) {
                Prefs.setInt(this, "anim", it)
                Panel.update(this)
                restyle()
            },
            g.sub("Carousel moves one icon at a time, Pages a whole row. Works with more than 6 apps."),
            g.slider("Animation speed", 0, 100, cfg.speed, { speedText(it) },
                { Prefs.setInt(this, "swipe_speed", it) },
                { Panel.update(this) }),
            sw("Scroll arrows ( \u2039  \u203A )", "Tap to move the icons yourself", "arrows", false) { Panel.update(this) },
            sw("Show app names under icons", null, "names", true) { Panel.update(this) },
            g.slider("Icon size", 0, 100, cfg.iconSize, { "${32 + it / 5} dp" },
                { Prefs.setInt(this, "icon_size", it); load(); fillPreview() },
                { Panel.update(this) }),
            g.sub("Notification icon size changes on Android 12 or newer.")
        ))

        // Apps
        col.addView(section("Apps",
            sw("Frequently used apps", "Fills free slots with the apps you open most. Your picks always stay.",
                "frequent", false) { Panel.update(this); fillPreview() },
            g.button("Reset usage counts", false) {
                Prefs.setList(this, "usage", emptyList())
                Panel.update(this)
                fillPreview()
                Toast.makeText(this, "Usage counts cleared", Toast.LENGTH_SHORT).show()
            },
            sw("Start after reboot", null, "boot", true)
        ))

        // Badges
        val granted = Panel.badgeAccess(this)
        col.addView(section("Notification badges",
            g.switchRow("Show unread counts on icons", "Needs Notification access", cfg.badges) { on ->
                Prefs.setBool(this, "badges", on)
                if (on && !Panel.badgeAccess(this)) {
                    Toast.makeText(this, "Allow access for Quick Apps", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                Panel.update(this)
            },
            g.sub(if (granted) "Notification access: allowed" else "Notification access: not allowed yet"),
            g.button("Hide badges for some apps", false) { chooseHidden() }
        ))

        // Haptics
        col.addView(section("Haptic feedback",
            sw("Vibrate when scrolling or tapping arrows", null, "haptics", true),
            g.slider("Intensity", 0, 100, cfg.hapticLevel, { "$it%" },
                { Prefs.setInt(this, "haptic_level", it) },
                { Haptics.tick(this, Cfg(this), true) }),
            g.button("Test vibration", false) { Haptics.tick(this, Cfg(this), true) }
        ))

        // Accessibility
        col.addView(section("Accessibility",
            sw("Reduce blur", null, "reduce_blur", false) { restyle() },
            sw("Reduce transparency", "Solid background", "reduce_transparency", false) { restyle() },
            sw("Reduce motion", "Stops automatic icon movement", "reduce_motion", false) { Panel.update(this); restyle() },
            sw("High contrast", "Solid black or white, strong outlines", "high_contrast", false) { restyle() }
        ))

        // Performance
        col.addView(section("Performance",
            sw("Low power mode", "No blur, shadows or animation, fewer refreshes", "low_power", false) {
                Panel.update(this)
                restyle()
            },
            g.button("Reset look to defaults", false) {
                Prefs.resetLook(this)
                Panel.update(this)
                restyle()
            }
        ))

        setContentView(scroll)
    }
}
