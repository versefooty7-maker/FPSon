package com.fpspulse.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import kotlin.math.abs

data class Stats(
    val batteryPct: Int,
    val charging: Boolean,
    val batteryTempC: Float,
    val currentMa: Int,
    val voltageV: Float,
    val cpuTempC: Float?,
    val ramUsedPct: Int,
    val ramFreeMb: Long,
    val ramTotalMb: Long
)

object SystemStats {

    private var thermalFiles: List<File>? = null

    fun read(ctx: Context): Stats {
        val bi: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val tempC = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = (bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Most devices report microamps, a few report milliamps.
        val ma = if (raw == Int.MIN_VALUE) 0 else {
            val a = abs(raw)
            if (a >= 10_000) a / 1000 else a
        }

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / (1024 * 1024)
        val freeMb = mi.availMem / (1024 * 1024)
        val usedPct = if (totalMb > 0) (100 - (freeMb * 100 / totalMb)).toInt() else 0

        return Stats(pct, charging, tempC, ma, voltage, readCpuTemp(), usedPct, freeMb, totalMb)
    }

    /** Best effort: many phones block this file; null means unreadable. */
    private fun readCpuTemp(): Float? {
        try {
            if (thermalFiles == null) {
                val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                val picked = ArrayList<File>()
                zones?.forEach { z ->
                    val type = try { File(z, "type").readText().trim().lowercase() } catch (_: Exception) { "" }
                    if (type.contains("cpu") || type.contains("tsens") || type.contains("soc") || type.contains("mtktscpu")) {
                        picked.add(File(z, "temp"))
                    }
                }
                thermalFiles = picked
            }
            var best: Float? = null
            for (f in thermalFiles.orEmpty()) {
                val v = try { f.readText().trim().toFloat() } catch (_: Exception) { continue }
                val c = if (v > 1000f) v / 1000f else v
                if (c in 5f..130f && (best == null || c > best)) best = c
            }
            return best
        } catch (_: Exception) {
            return null
        }
    }
}
