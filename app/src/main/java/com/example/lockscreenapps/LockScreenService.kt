package com.example.lockscreenapps

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

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

// Draws a red unread-count bubble on the top-right corner of an icon
private fun withBadge(src: Bitmap, count: Int): Bitmap {
    val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bmp)
    val r = bmp.width * 0.24f
    val cx = bmp.width - r
    val cy = r
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.RED
    canvas.drawCircle(cx, cy, r, paint)
    paint.color = Color.WHITE
    paint.textSize = r * 1.15f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    val label = if (count > 99) "99+" else count.toString()
    canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
    return bmp
}

object BadgeStore {
    @Volatile
    var counts: Map<String, Int> = emptyMap()
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.bool(context, "enabled", false) &&
            Prefs.bool(context, "boot", true)
        ) {
            Panel.start(context)
        }
    }
}

// Opens an app from the notification and counts the launch for "frequently used"
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        if (pkg != null) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Prefs.bumpUsage(this, pkg)
                startActivity(launch)
                LockScreenService.refresh(this)
            }
        }
        finish()
    }
}

// Counts unread notifications per app (only runs if you allow Notification access)
class BadgeListener : NotificationListenerService() {

    private fun recompute() {
        val counts = HashMap<String, Int>()
        try {
            val active = activeNotifications
            if (active != null) {
                for (sbn in active) {
                    val n = sbn.notification
                    if (sbn.packageName == packageName) continue
                    if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                    if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) continue
                    counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
        }
        BadgeStore.counts = counts
        LockScreenService.refresh(this)
    }

    override fun onListenerConnected() {
        recompute()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recompute()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recompute()
    }
}

class LockScreenService : Service() {

    companion object {
        @Volatile
        var instance: LockScreenService? = null

        fun refresh(ctx: Context) {
            instance?.requestRender()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var offset = 0
    private var screenOn = true
    private var entryCount = 0

    private val nm: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val renderNow = Runnable { nm.notify(1, render()) }

    // Waits a moment so a burst of changes only redraws once
    fun requestRender() {
        handler.removeCallbacks(renderNow)
        val delay = if (Cfg(this).lowPower) 2000L else 400L
        handler.postDelayed(renderNow, delay)
    }

    // Moves the icons automatically at the chosen speed
    private val tick = object : Runnable {
        override fun run() {
            val cfg = Cfg(this@LockScreenService)
            if (screenOn && cfg.autoAnim != 0 && entryCount > WINDOW) {
                offset += if (cfg.autoAnim == 2) WINDOW else 1
                nm.notify(1, render())
            }
            scheduleTick()
        }
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        val cfg = Cfg(this)
        if (cfg.autoAnim != 0) handler.postDelayed(tick, cfg.intervalMs.toLong())
    }

    // Pauses everything while the screen is off, to save battery
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
        instance = this
        nm.createNotificationChannel(
            NotificationChannel("lock_screen_apps", "Quick Apps", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun actionPi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, LockScreenService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun render(): Notification {
        val cfg = Cfg(this)
        val entries = Panel.order(this)
        val n = entries.size
        entryCount = n

        val badgesOn = cfg.badges && Panel.badgeAccess(this)
        val hidden = Prefs.list(this, "badge_hidden")
        val counts = BadgeStore.counts

        val windowed = n > WINDOW && (cfg.autoAnim != 0 || cfg.arrows)
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
                val pkg = entries[idx]
                val launch = Intent(this, LaunchActivity::class.java)
                    .setData(Uri.fromParts("app", pkg, null))
                    .putExtra("pkg", pkg)
                val pi = PendingIntent.getActivity(
                    this, pkg.hashCode(), launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                var bmp = packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
                val c = counts[pkg] ?: 0
                if (badgesOn && c > 0 && pkg !in hidden) bmp = withBadge(bmp, c)

                big.setViewVisibility(SLOT_IDS[i], View.VISIBLE)
                big.setImageViewBitmap(APP_IDS[i], bmp)
                big.setOnClickPendingIntent(SLOT_IDS[i], pi)
                if (cfg.names) {
                    big.setViewVisibility(NAME_IDS[i], View.VISIBLE)
                    big.setTextViewText(NAME_IDS[i], Panel.label(this, pkg))
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

        // Icon size (Android 12 and newer)
        if (Build.VERSION.SDK_INT >= 31) {
            for (i in 0 until MAX_APPS) {
                big.setViewLayoutHeight(APP_IDS[i], cfg.iconDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
            for (i in 0 until WINDOW) {
                small.setViewLayoutHeight(APP_IDS[i], minOf(cfg.iconDp, 48).toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        big.setViewVisibility(R.id.row2, if (windowed || n <= WINDOW) View.GONE else View.VISIBLE)

        val arrowVisibility = if (windowed && cfg.arrows) View.VISIBLE else View.GONE
        for (v in listOf(small, big)) {
            v.setViewVisibility(R.id.prev, arrowVisibility)
            v.setViewVisibility(R.id.next, arrowVisibility)
            v.setOnClickPendingIntent(R.id.prev, actionPi(ACTION_PREV, 201))
            v.setOnClickPendingIntent(R.id.next, actionPi(ACTION_NEXT, 202))
        }

        val settingsPi = PendingIntent.getActivity(
            this, 100, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "lock_screen_apps")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Quick Apps")
            .setContentText(if (n == 0) "Tap to choose your apps" else "Tap here for settings")
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
            val cfg = Cfg(this)
            val step = if (cfg.anim == 2) WINDOW else 1
            offset += if (action == ACTION_NEXT) step else -step
            Haptics.tick(this, cfg)
            nm.notify(1, render())
        } else {
            startForeground(1, render())
        }
        scheduleTick()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
