package com.fpspulse.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

/** Live values shared with the app UI (written on the main thread by the service). */
object ServiceState {
    var running by mutableStateOf(false)
    var fps by mutableStateOf(0f)
    var frameMs by mutableStateOf(0f)
    var low1 by mutableStateOf<Float?>(null)
    var stats by mutableStateOf<Stats?>(null)
    var sessionAvg by mutableStateOf(0)
    var sessionMax by mutableStateOf(0)
    var sessionMin by mutableStateOf(0)
    var sessionSeconds by mutableStateOf(0)
    var history by mutableStateOf<List<Float>>(emptyList())
}

class FpsService : Service() {

    companion object {
        const val ACTION_START = "com.fpspulse.app.START"
        const val ACTION_STOP = "com.fpspulse.app.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_APP_LABEL = "appLabel"
        private const val CHANNEL_ID = "fpspulse_overlay"
        private const val NOTIF_ID = 4711
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager

    private var engine: FpsEngine? = null
    private var overlay: OverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var started = false
    private var stopping = false

    private var appLabel = "Manual session"
    private var startMs = 0L
    private var sumFps = 0L
    private var nFps = 0
    private var minFps = Int.MAX_VALUE
    private var maxFps = 0
    private var minLow1 = Int.MAX_VALUE
    private var maxTemp = 0f
    private var seconds = 0
    private val hist = ArrayList<Float>()

    private var lastScale = -1f
    private var lastCompact = false

    private val tick = object : Runnable {
        override fun run() {
            val eng = engine ?: return
            val snap = eng.snapshot()
            val st = SystemStats.read(this@FpsService)

            seconds++
            if (snap.fps >= 1f) {
                val f = snap.fps.toInt()
                sumFps += f
                nFps++
                if (f < minFps) minFps = f
                if (f > maxFps) maxFps = f
                val lowVal = snap.low1Fps
                if (lowVal != null && lowVal.toInt() < minLow1) {
                    minLow1 = lowVal.toInt()
                }
            }
            if (st.batteryTempC > maxTemp) maxTemp = st.batteryTempC

            hist.add(snap.fps)
            if (hist.size > 60) hist.removeAt(0)

            ServiceState.fps = snap.fps
            ServiceState.frameMs = snap.frameTimeMs
            ServiceState.low1 = snap.low1Fps
            ServiceState.stats = st
            ServiceState.sessionSeconds = seconds
            ServiceState.sessionAvg = if (nFps > 0) (sumFps / nFps).toInt() else 0
            ServiceState.sessionMax = maxFps
            ServiceState.sessionMin = if (minFps == Int.MAX_VALUE) 0 else minFps
            ServiceState.history = ArrayList(hist)

            overlay?.let { v ->
                if (lastScale != prefs.scale || lastCompact != prefs.compact) {
                    lastScale = prefs.scale
                    lastCompact = prefs.compact
                    v.requestLayout()
                }
                v.alpha = prefs.opacity
                v.update(snap, st)
            }
            main.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action != ACTION_START || started) {
            return START_NOT_STICKY
        }

        // The foreground notification has to be up before touching MediaProjection.
        try {
            startAsForeground()
        } catch (e: Exception) {
            fail("Could not start the monitor: ${e.message}")
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            fail("Allow \"Display over other apps\" first")
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            fail("Screen capture permission is missing")
            return START_NOT_STICKY
        }
        appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: "Manual session"

        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("capture permission was not granted")

            val e = FpsEngine(projection) {
                main.post { stopSelf() }
            }
            val dm = resources.displayMetrics
            e.start(dm.widthPixels, dm.heightPixels, dm.densityDpi)
            engine = e

            addOverlay()
            started = true
            startMs = System.currentTimeMillis()
            ServiceState.running = true
            main.postDelayed(tick, 1000L)
        } catch (e: Exception) {
            fail("Could not start capture: ${e.message}")
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FPS monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FpsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("FPS Pulse is running")
            .setContentText("Live FPS overlay is on")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun addOverlay() {
        val v = OverlayView(this, prefs)
        v.alpha = prefs.opacity
        lastScale = prefs.scale
        lastCompact = prefs.compact

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = prefs.overlayX
        lp.y = prefs.overlayY
        params = lp

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        v.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (startX + (ev.rawX - downX)).toInt().coerceAtLeast(0)
                    lp.y = (startY + (ev.rawY - downY)).toInt().coerceAtLeast(0)
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.overlayX = lp.x
                    prefs.overlayY = lp.y
                    true
                }
                else -> false
            }
        }
        wm.addView(v, lp)
        overlay = v
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun saveSession() {
        if (!started || seconds < 5 || nFps == 0) return
        prefs.addSession(
            SessionRecord(
                app = appLabel,
                startMs = startMs,
                durationSec = seconds,
                minFps = if (minFps == Int.MAX_VALUE) 0 else minFps,
                avgFps = (sumFps / nFps).toInt(),
                maxFps = maxFps,
                low1Fps = if (minLow1 == Int.MAX_VALUE) 0 else minLow1,
                maxTempC = maxTemp
            )
        )
    }

    override fun onDestroy() {
        if (!stopping) {
            stopping = true
            main.removeCallbacks(tick)
            saveSession()
            engine?.stop()
            engine = null
            overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            overlay = null
            ServiceState.running = false
            ServiceState.fps = 0f
            ServiceState.history = emptyList()
        }
        super.onDestroy()
    }
}
