package com.example.lockscreenapps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : GlassActivity() {

    private class Row(val view: View, val label: String)

    private val selected = LinkedHashSet<String>()
    private lateinit var stripRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun build() {
        selected.clear()
        selected.addAll(Prefs.load(this))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(g.px(16), g.px(12), g.px(16), g.px(12))

        // Header
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(g.title("Quick Apps"), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(g.button("Settings", false) {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            finish()
        })
        root.addView(header)

        // Swipeable preview of your panel
        val strip = HorizontalScrollView(this)
        strip.isHorizontalScrollBarEnabled = false
        stripRow = LinearLayout(this)
        strip.addView(stripRow)
        var lastIdx = -1
        val itemW = g.px(cfg.iconDp + 32)
        strip.setOnScrollChangeListener { _, x, _, _, _ ->
            val idx = x / itemW
            if (idx != lastIdx) {
                if (lastIdx != -1) Haptics.tick(this@MainActivity, cfg)
                lastIdx = idx
            }
        }
        root.addView(g.card(g.label("Your panel (swipe sideways)"), strip))
        fillStrip()

        // Search
        val search = EditText(this)
        search.hint = "Search apps"
        search.setSingleLine(true)
        search.setTextColor(pal.text)
        search.setHintTextColor(pal.sub)
        search.background = g.roundRect(pal.card, 14, pal.stroke)
        search.setPadding(g.px(14), g.px(10), g.px(14), g.px(10))
        val searchLp = LinearLayout.LayoutParams(MATCH, WRAP)
        searchLp.setMargins(0, g.px(4), 0, g.px(8))
        root.addView(search, searchLp)

        // App list
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val rows = mutableListOf<Row>()
        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(packageManager).toString()

            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, g.px(4), 0, g.px(4))

            val icon = ImageView(this)
            icon.setImageDrawable(ri.loadIcon(packageManager))
            row.addView(icon, LinearLayout.LayoutParams(g.px(40), g.px(40)))

            val box = CheckBox(this)
            box.text = label
            box.setTextColor(pal.text)
            box.buttonTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            box.setPadding(g.px(12), 0, 0, 0)
            box.isChecked = pkg in selected
            box.setOnCheckedChangeListener { b, checked ->
                if (checked) {
                    if (selected.size >= MAX_APPS && pkg !in selected) {
                        b.isChecked = false
                        Toast.makeText(this@MainActivity, "Maximum is $MAX_APPS apps", Toast.LENGTH_SHORT).show()
                    } else {
                        selected.add(pkg)
                    }
                } else {
                    selected.remove(pkg)
                }
                // Saved automatically, in the order you picked them
                Prefs.save(this@MainActivity, selected.toList())
                fillStrip()
                Panel.update(this@MainActivity)
            }
            row.addView(box)
            list.addView(row)
            rows.add(Row(row, label))
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                for (r in rows) {
                    r.view.visibility =
                        if (q.isEmpty() || r.label.lowercase().contains(q)) View.VISIBLE else View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Buttons
        val bottom = LinearLayout(this)
        val showBtn = g.button("Show on lock screen", true) {
            Prefs.setBool(this@MainActivity, "enabled", true)
            Panel.start(this@MainActivity)
            Toast.makeText(this@MainActivity, "Shown on lock screen", Toast.LENGTH_SHORT).show()
        }
        val offBtn = g.button("Turn off", false) {
            stopService(Intent(this@MainActivity, LockScreenService::class.java))
            Prefs.setBool(this@MainActivity, "enabled", false)
            Toast.makeText(this@MainActivity, "Turned off", Toast.LENGTH_SHORT).show()
        }
        val half = LinearLayout.LayoutParams(0, WRAP, 1f)
        half.setMargins(g.px(4), 0, g.px(4), 0)
        bottom.addView(showBtn, half)
        bottom.addView(offBtn, LinearLayout.LayoutParams(half))
        root.addView(bottom)

        setContentView(root)
    }

    private fun fillStrip() {
        stripRow.removeAllViews()
        val order = Panel.order(this)
        if (order.isEmpty()) {
            stripRow.addView(g.sub("No apps yet. Tick some below."))
            return
        }
        for (pkg in order) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(g.px(8), g.px(4), g.px(8), g.px(4))

            val icon = ImageView(this)
            icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
            item.addView(icon, LinearLayout.LayoutParams(g.px(cfg.iconDp), g.px(cfg.iconDp)))

            if (cfg.names) {
                val t = TextView(this)
                t.text = Panel.label(this, pkg)
                t.setTextColor(pal.text)
                t.textSize = 11f
                t.maxLines = 1
                t.ellipsize = TextUtils.TruncateAt.END
                t.gravity = Gravity.CENTER
                item.addView(t, LinearLayout.LayoutParams(g.px(cfg.iconDp + 16), WRAP))
            }
            item.setOnClickListener {
                startActivity(Intent(this@MainActivity, LaunchActivity::class.java).putExtra("pkg", pkg))
            }
            stripRow.addView(item)
        }
    }
}
