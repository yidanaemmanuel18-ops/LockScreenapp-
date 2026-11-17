package com.example.lockscreenapps

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// Edit this list: package names of apps you want on the lock screen (max 3)
val QUICK_APPS = listOf(
    "com.android.chrome" to "Chrome",
    "com.google.android.apps.maps" to "Maps",
    "com.whatsapp" to "WhatsApp"
)

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            startAndClose()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        startAndClose()
    }

    private fun startAndClose() {
        ContextCompat.startForegroundService(this, Intent(this, LockScreenService::class.java))
        finish()
    }
}

class LockScreenService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "lock_screen_apps"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(channelId, "Quick Apps", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Quick Apps")
            .setContentText("Tap an app below")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        QUICK_APPS.take(3).forEachIndexed { i, (pkg, label) ->
            packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(
                    this, i, launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(0, label, pi)
            }
        }

        startForeground(1, builder.build())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
