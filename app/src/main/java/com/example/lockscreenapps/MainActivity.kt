package com.example.lockscreenapps

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

const val MAX_APPS = 12
const val WINDOW = 6
const val ACTION_NEXT = "com.example.lockscreenapps.NEXT"
const val ACTION_PREV = "com.example.lockscreenapps.PREV"

// Animation speeds: how often the icons move (milliseconds)
val SPEEDS_MS = intArrayOf(4000, 2500, 1500, 800)

private val APP_IDS = intArrayOf(
    R.id.app0, R.id.app1, R.id.app2, R.id.app3, R.id.app4, R.id.app5,
    R.id.app6, R.id.app7, R.id.app8, R.id.app9, R.id.app10, R.id.app11
)
private val SLOT_IDS = intArrayOf(
    R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3, R.id.slot4, R.id.slot5,
    R.id.slot6, R.id.slot7, R.id.slot8, R.id.slot9, R.id.slot10, R.id.slot11
)
private val NAME_IDS = intArrayOf(
    R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5,
    R.id.name6, R.id.name7, R.id.name8, R.id.name9, R.id.name10, R.id.name11
)

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("quick_apps", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<String> =
        sp(ctx).getString("apps", "").orEmpty().split(",").filter { it.isNotBlank() }

    fun save(ctx: Context, apps: List<String>) {
        sp(ctx).edit().putString("apps", apps.joinToString(",")).apply()
    }

    fun bool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)

    fun setBool(ctx: Context, key: String, value: Boolean) {
        sp(ctx).edit().putBoolean(key, value).apply()
    }

    fun int(ctx: Context, key: String, def: Int): Int = sp(ctx).getInt(key, def)

    fun setInt(ctx: Context, key: String, value: Int) {
        sp(ctx).edit().putInt(key, value).apply()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.bool(context, "enabled", false) &&
            Prefs.bool(context, "boot", true)
        ) {
            ContextCompat.startForegroundService(
                context, Intent(context, LockScreenService::class.java)
            )
        }
    }
}

class MainActivity : Activity() {

    private class Row(val pkg: String, val view: View, val label: String, val box: CheckBox)
    private val rows = mutableListOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Follow the phone's dark / light mode
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        setTheme(
            if (night) android.R.style.Theme_DeviceDefault
            else android.R.style.Theme_DeviceDefault_Light
        )
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(16))
        }
        root.addView(TextView(this).apply {
            text = "Choose up to $MAX_APPS apps for your lock screen"
            textSize = 16f
        })

        val search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine(true)
        }
        root.addView(search)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val saved = Prefs.load(this).toSet()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(packageManager).toString()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(4), 0, px(4))
            }
            val icon = ImageView(this).apply { setImageDrawable(ri.loadIcon(packageManager)) }
            row.addView(icon, LinearLayout.LayoutParams(px(40), px(40)))

            val box = CheckBox(this).apply {
                text = label
                isChecked = pkg in saved
                setPadding(px(12), 0, 0, 0)
            }
            box.setOnCheckedChangeListener { b, checked ->
                if (checked && rows.count { it.box.isChecked } > MAX_APPS) {
                    b.isChecked = false
                    Toast.makeText(this, "Maximum is $MAX_APPS apps", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(box)
            list.addView(row)
            rows.add(Row(pkg, row, label, box))
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                rows.forEach {
                    it.view.visibility =
                        if (q.isEmpty() || it.label.lowercase().contains(q)) View.VISIBLE else View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val save = Button(this).apply {
            text = "Save and show on lock screen"
            setOnClickListener {
                Prefs.save(this@MainActivity, rows.filter { it.box.isChecked }.map { it.pkg })
                Prefs.setBool(this@MainActivity, "enabled", true)
                startLockService()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        val stop = Button(this).apply {
            text = "Turn off"
            setOnClickListener { turnOff() }
        }
        root.addView(save)
        root.addView(stop)
        setContentView(root)
    }

    private fun startLockService() {
        ContextCompat.startForegroundService(this, Intent(this, LockScreenService::class.java))
    }

    private fun turnOff() {
        stopService(Intent(this, LockScreenService::class.java))
        Prefs.setBool(this, "enabled", false)
        Toast.makeText(this, "Turned off", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Settings")
        menu.add(0, 2, 1, "Select none")
        menu.add(0, 3, 2, "Turn off")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> showSettings()
            2 -> rows.forEach { it.box.isChecked = false }
            3 -> turnOff()
        }
        return true
    }

    private fun showSettings() {
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        fun check(title: String, key: String, def: Boolean): CheckBox {
            val c = CheckBox(this).apply {
                text = title
                isChecked = Prefs.bool(this@MainActivity, key, def)
            }
            box.addView(c)
            return c
        }

        fun heading(title: String) {
            box.addView(TextView(this).apply {
                text = title
                setPadding(0, pad / 2, 0, 0)
            })
        }

        fun spinner(options: Array<String>, selected: Int): Spinner {
            val s = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    options
                )
                setSelection(selected)
            }
            box.addView(s)
            return s
        }

        val boot = check("Start automatically after reboot", "boot", true)
        val names = check("Show app names under icons", "names", true)
        val arrows = check("Show scroll arrows ( \u2039  \u203A ) in notification", "arrows", false)

        heading("Animation style")
        val animSpinner = spinner(
            arrayOf("Off (show all icons)", "Carousel (one icon at a time)", "Pages (six icons at a time)"),
            Prefs.int(this, "anim", 0)
        )

        heading("Animation speed")
        val speedSpinner = spinner(
            arrayOf("Slow (4 seconds)", "Normal (2.5 seconds)", "Fast (1.5 seconds)", "Very fast (0.8 seconds)"),
            Prefs.int(this, "speed", 1)
        )

        heading("Animation and arrows only work when you pick more than 6 apps.")

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                Prefs.setBool(this, "boot", boot.isChecked)
                Prefs.setBool(this, "names", names.isChecked)
                Prefs.setBool(this, "arrows", arrows.isChecked)
                Prefs.setInt(this, "anim", animSpinner.selectedItemPosition)
                Prefs.setInt(this, "speed", speedSpinner.selectedItemPosition)
                if (Prefs.bool(this, "enabled", false)) startLockService()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class LockScreenService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var offset = 0
    private var screenOn = true
    private var entryCount = 0

    private val nm: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Moves the icons automatically at the chosen speed
    private val tick = object : Runnable {
        override fun run() {
            val mode = Prefs.int(this@LockScreenService, "anim", 0)
            if (screenOn && mode != 0 && entryCount > WINDOW) {
                offset += if (mode == 2) WINDOW else 1
                nm.notify(1, render())
            }
            scheduleTick()
        }
    }

    // Pauses the animation while the screen is off, to save battery
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            if (screenOn) {
                nm.notify(1, render())
                scheduleTick()
            } else {
                handler.removeCallbacks(tick)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(
            NotificationChannel("lock_screen_apps", "Quick Apps", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        if (Prefs.int(this, "anim", 0) != 0) {
            val i = Prefs.int(this, "speed", 1).coerceIn(0, SPEEDS_MS.size - 1)
            handler.postDelayed(tick, SPEEDS_MS[i].toLong())
        }
    }

    private fun labelOf(pkg: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

    private fun actionPi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, LockScreenService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun render(): Notification {
        val entries = Prefs.load(this)
            .mapNotNull { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { pkg to it } }
            .take(MAX_APPS)
        val n = entries.size
        entryCount = n

        val mode = Prefs.int(this, "anim", 0)
        val arrows = Prefs.bool(this, "arrows", false)
        val showNames = Prefs.bool(this, "names", true)
        val windowed = n > WINDOW && (mode != 0 || arrows)
        offset = if (n > 0) ((offset % n) + n) % n else 0

        val small = RemoteViews(packageName, R.layout.notif_collapsed)
        val big = RemoteViews(packageName, R.layout.notif_expanded)

        for (i in 0 until MAX_APPS) {
            val idx = when {
                windowed -> if (i < WINDOW) (offset + i) % n else -1
                i < n -> i
                else -> -1
            }
            if (idx >= 0) {
                val (pkg, launch) = entries[idx]
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(
                    this, pkg.hashCode(), launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val bmp = packageManager.getApplicationIcon(pkg).toBitmap(96, 96)

                big.setViewVisibility(SLOT_IDS[i], View.VISIBLE)
                big.setImageViewBitmap(APP_IDS[i], bmp)
                big.setOnClickPendingIntent(SLOT_IDS[i], pi)
                if (showNames) {
                    big.setViewVisibility(NAME_IDS[i], View.VISIBLE)
                    big.setTextViewText(NAME_IDS[i], labelOf(pkg))
                } else {
                    big.setViewVisibility(NAME_IDS[i], View.GONE)
                }
                if (i < WINDOW) {
                    small.setViewVisibility(APP_IDS[i], View.VISIBLE)
                    small.setImageViewBitmap(APP_IDS[i], bmp)
                    small.setOnClickPendingIntent(APP_IDS[i], pi)
                }
            } else {
                big.setViewVisibility(SLOT_IDS[i], View.INVISIBLE)
                if (i < WINDOW) small.setViewVisibility(APP_IDS[i], View.INVISIBLE)
            }
        }

        big.setViewVisibility(R.id.row2, if (windowed || n <= WINDOW) View.GONE else View.VISIBLE)

        val arrowVisibility = if (windowed && arrows) View.VISIBLE else View.GONE
        for (v in listOf(small, big)) {
            v.setViewVisibility(R.id.prev, arrowVisibility)
            v.setViewVisibility(R.id.next, arrowVisibility)
            v.setOnClickPendingIntent(R.id.prev, actionPi(ACTION_PREV, 201))
            v.setOnClickPendingIntent(R.id.next, actionPi(ACTION_NEXT, 202))
        }

        val settingsPi = PendingIntent.getActivity(
            this, 100, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "lock_screen_apps")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Quick Apps")
            .setContentText(if (n == 0) "Tap to choose your apps" else "Tap here to edit apps")
            .setContentIntent(settingsPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (n > 0) {
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(big)
        }
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_NEXT || action == ACTION_PREV) {
            val step = if (Prefs.int(this, "anim", 0) == 2) WINDOW else 1
            offset += if (action == ACTION_NEXT) step else -step
            nm.notify(1, render())
        } else {
            startForeground(1, render())
        }
        scheduleTick()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
