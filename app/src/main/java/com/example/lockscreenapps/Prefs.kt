package com.example.lockscreenapps

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

const val MAX_APPS = 12
const val WINDOW = 6
const val ACTION_NEXT = "com.example.lockscreenapps.NEXT"
const val ACTION_PREV = "com.example.lockscreenapps.PREV"

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("quick_apps", Context.MODE_PRIVATE)

    fun list(ctx: Context, key: String): List<String> =
        sp(ctx).getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }

    fun setList(ctx: Context, key: String, value: List<String>) {
        sp(ctx).edit().putString(key, value.joinToString(",")).apply()
    }

    fun load(ctx: Context): List<String> = list(ctx, "apps")

    fun save(ctx: Context, apps: List<String>) {
        setList(ctx, "apps", apps)
    }

    fun bool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)

    fun setBool(ctx: Context, key: String, value: Boolean) {
        sp(ctx).edit().putBoolean(key, value).apply()
    }

    fun int(ctx: Context, key: String, def: Int): Int = sp(ctx).getInt(key, def)

    fun setInt(ctx: Context, key: String, value: Int) {
        sp(ctx).edit().putInt(key, value).apply()
    }

    // Launch counts, stored as "pkg=count,pkg=count"
    fun usage(ctx: Context): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (item in list(ctx, "usage")) {
            val parts = item.split("=")
            if (parts.size == 2) out[parts[0]] = parts[1].toIntOrNull() ?: 0
        }
        return out
    }

    fun bumpUsage(ctx: Context, pkg: String) {
        val m = usage(ctx).toMutableMap()
        m[pkg] = (m[pkg] ?: 0) + 1
        // Older launches slowly count for less, so rankings follow recent habits
        if (m.values.sum() > 400) {
            for (k in m.keys.toList()) m[k] = (m[k] ?: 0) / 2
        }
        setList(ctx, "usage", m.filter { it.value > 0 }.map { it.key + "=" + it.value })
    }

    fun resetLook(ctx: Context) {
        val keys = listOf(
            "theme", "bg", "accent", "hue", "opacity", "blur",
            "reduce_blur", "reduce_transparency", "reduce_motion", "high_contrast", "low_power"
        )
        val e = sp(ctx).edit()
        for (k in keys) e.remove(k)
        e.apply()
    }
}

class Cfg(ctx: Context) {
    val theme = Prefs.int(ctx, "theme", 0)            // 0 system, 1 light, 2 dark
    val bg = Prefs.int(ctx, "bg", 0)
    val accentIdx = Prefs.int(ctx, "accent", 0)       // 0-5 swatches, 6 = custom hue
    val accentHue = Prefs.int(ctx, "hue", 210)
    val opacity = Prefs.int(ctx, "opacity", 70)
    val blur = Prefs.int(ctx, "blur", 50)
    val anim = Prefs.int(ctx, "anim", 0)              // 0 off, 1 carousel, 2 pages
    val speed = Prefs.int(ctx, "swipe_speed", 40)
    val arrows = Prefs.bool(ctx, "arrows", false)
    val names = Prefs.bool(ctx, "names", true)
    val iconSize = Prefs.int(ctx, "icon_size", 50)
    val haptics = Prefs.bool(ctx, "haptics", true)
    val hapticLevel = Prefs.int(ctx, "haptic_level", 50)
    val badges = Prefs.bool(ctx, "badges", false)
    val frequent = Prefs.bool(ctx, "frequent", false)
    val boot = Prefs.bool(ctx, "boot", true)
    val reduceBlur = Prefs.bool(ctx, "reduce_blur", false)
    val reduceTransparency = Prefs.bool(ctx, "reduce_transparency", false)
    val reduceMotion = Prefs.bool(ctx, "reduce_motion", false)
    val highContrast = Prefs.bool(ctx, "high_contrast", false)
    val lowPower = Prefs.bool(ctx, "low_power", false)

    val noBlur: Boolean get() = reduceBlur || highContrast || lowPower
    val autoAnim: Int get() = if (reduceMotion || lowPower) 0 else anim
    val intervalMs: Int get() = (6000 - speed * 55).coerceAtLeast(500)
    val iconDp: Int get() = 32 + iconSize / 5
}

object Panel {
    fun label(ctx: Context, pkg: String): String =
        try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

    private fun launchable(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    // Pinned apps always stay. With "frequently used" on, free slots fill with your most-used apps
    // and everything is ranked by how often you open it.
    fun order(ctx: Context): List<String> {
        val cfg = Cfg(ctx)
        val pinned = Prefs.load(ctx).filter { launchable(ctx, it) }
        if (!cfg.frequent) return pinned.take(MAX_APPS)
        val usage = Prefs.usage(ctx)
        val extra = usage.entries
            .filter { it.key !in pinned }
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { launchable(ctx, it) }
        val chosen = (pinned + extra).take(MAX_APPS)
        return chosen.sortedByDescending { usage[it] ?: 0 }
    }

    fun start(ctx: Context) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, LockScreenService::class.java))
    }

    // Re-draws the notification if it is switched on
    fun update(ctx: Context) {
        if (Prefs.bool(ctx, "enabled", false)) start(ctx)
    }

    fun badgeAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}

object Haptics {
    private var last = 0L

    fun tick(ctx: Context, cfg: Cfg, force: Boolean = false) {
        if (!force && (!cfg.haptics || cfg.lowPower || cfg.hapticLevel == 0)) return
        val now = System.currentTimeMillis()
        if (!force && now - last < 60) return   // never buzz in rapid bursts
        last = now

        val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vib.hasVibrator()) return

        val amp = (cfg.hapticLevel * 255 / 100).coerceIn(1, 255)
        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(18, amp)
        } else {
            VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vib.vibrate(effect)
    }
}
