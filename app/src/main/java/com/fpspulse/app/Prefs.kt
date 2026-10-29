package com.fpspulse.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SessionRecord(
    val app: String,
    val startMs: Long,
    val durationSec: Int,
    val minFps: Int,
    val avgFps: Int,
    val maxFps: Int,
    val low1Fps: Int,
    val maxTempC: Float
)

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("fpspulse", Context.MODE_PRIVATE)

    var overlayX: Int
        get() = sp.getInt("ox", 24)
        set(v) = sp.edit().putInt("ox", v).apply()

    var overlayY: Int
        get() = sp.getInt("oy", 120)
        set(v) = sp.edit().putInt("oy", v).apply()

    /** 0.30 .. 1.00 */
    var opacity: Float
        get() = sp.getFloat("opacity", 0.92f)
        set(v) = sp.edit().putFloat("opacity", v).apply()

    /** 0.70 .. 1.60 */
    var scale: Float
        get() = sp.getFloat("scale", 1.0f)
        set(v) = sp.edit().putFloat("scale", v).apply()

    var compact: Boolean
        get() = sp.getBoolean("compact", false)
        set(v) = sp.edit().putBoolean("compact", v).apply()

    var showGraph: Boolean
        get() = sp.getBoolean("graph", true)
        set(v) = sp.edit().putBoolean("graph", v).apply()

    var showBattery: Boolean
        get() = sp.getBoolean("battery", true)
        set(v) = sp.edit().putBoolean("battery", v).apply()

    var showTemp: Boolean
        get() = sp.getBoolean("temp", true)
        set(v) = sp.edit().putBoolean("temp", v).apply()

    var showRam: Boolean
        get() = sp.getBoolean("ram", true)
        set(v) = sp.edit().putBoolean("ram", v).apply()

    var showLow1: Boolean
        get() = sp.getBoolean("low1", true)
        set(v) = sp.edit().putBoolean("low1", v).apply()

    /** Overlay border turns red above this battery temperature (°C). */
    var heatWarnC: Int
        get() = sp.getInt("heatWarn", 42)
        set(v) = sp.edit().putInt("heatWarn", v).apply()

    /** Target FPS used to colour the number (green near target). */
    var targetFps: Int
        get() = sp.getInt("targetFps", 60)
        set(v) = sp.edit().putInt("targetFps", v).apply()

    /** Package names the user added to the games list by hand. */
    var pinned: Set<String>
        get() = sp.getStringSet("pinned", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("pinned", HashSet(v)).apply()

    fun resetPosition() {
        sp.edit().putInt("ox", 24).putInt("oy", 120).apply()
    }

    fun sessions(): List<SessionRecord> {
        val raw = sp.getString("sessions", "[]") ?: "[]"
        val out = ArrayList<SessionRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    SessionRecord(
                        o.getString("app"), o.getLong("start"), o.getInt("dur"),
                        o.getInt("min"), o.getInt("avg"), o.getInt("max"),
                        o.getInt("low1"), o.getDouble("temp").toFloat()
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun addSession(r: SessionRecord) {
        val list = ArrayList(sessions())
        list.add(0, r)
        val arr = JSONArray()
        for (s in list.take(50)) {
            arr.put(
                JSONObject()
                    .put("app", s.app).put("start", s.startMs).put("dur", s.durationSec)
                    .put("min", s.minFps).put("avg", s.avgFps).put("max", s.maxFps)
                    .put("low1", s.low1Fps).put("temp", s.maxTempC.toDouble())
            )
        }
        sp.edit().putString("sessions", arr.toString()).apply()
    }

    fun clearSessions() = sp.edit().remove("sessions").apply()
}
