package com.fpspulse.app

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

data class FpsSnapshot(
    val fps: Float,
    val frameTimeMs: Float,
    val low1Fps: Float?,
    val frames: Int
)

/**
 * Measures the real on-screen frame rate.
 *
 * The screen is mirrored into a tiny VirtualDisplay (MediaProjection). The system only
 * delivers a new image when the display actually composes a new frame, so counting those
 * images gives the rate at which the screen content really updates, i.e. what the game
 * really renders to the display. No root and no Shizuku needed.
 */
class FpsEngine(
    private val projection: MediaProjection,
    private val onProjectionStopped: () -> Unit
) {
    private var thread: HandlerThread? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null

    private val lock = Any()
    private var count = 0
    private val stamps = ArrayDeque<Long>() // frame timestamps (ns) of the last ~10 s
    private var lastSnapshotMs = SystemClock.elapsedRealtime()

    @Volatile
    private var running = false

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            stop()
            onProjectionStopped()
        }
    }

    fun start(screenWidth: Int, screenHeight: Int, dpi: Int) {
        if (running) return
        running = true

        val t = HandlerThread("fps-capture").also { it.start() }
        thread = t
        val handler = Handler(t.looper)

        // Must be registered before createVirtualDisplay on Android 14+.
        projection.registerCallback(callback, handler)

        val w = 288
        val h = ((screenHeight.toFloat() / screenWidth.toFloat()) * w).toInt().coerceAtLeast(2)

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 4)
        reader = r
        r.setOnImageAvailableListener({ rd ->
            var img = try { rd.acquireNextImage() } catch (_: Exception) { null }
            while (img != null) {
                val ts = img.timestamp
                img.close()
                onFrame(if (ts > 0) ts else System.nanoTime())
                img = try { rd.acquireNextImage() } catch (_: Exception) { null }
            }
        }, handler)

        display = projection.createVirtualDisplay(
            "FpsPulseCapture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )
        synchronized(lock) {
            count = 0
            stamps.clear()
            lastSnapshotMs = SystemClock.elapsedRealtime()
        }
    }

    private fun onFrame(ts: Long) {
        synchronized(lock) {
            count++
            stamps.addLast(ts)
            val cutoff = ts - 10_000_000_000L
            while (stamps.size > 1 && stamps.first() < cutoff) stamps.removeFirst()
        }
    }

    /** Call about once per second. Returns the rate since the previous call. */
    fun snapshot(): FpsSnapshot {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val dt = (now - lastSnapshotMs).coerceAtLeast(1L)
            lastSnapshotMs = now
            val frames = count
            count = 0

            // The overlay itself redraws once per second, which shows up as one frame.
            // A single frame in a window therefore means "nothing is rendering".
            val fps = if (frames <= 1) 0f else frames * 1000f / dt

            var low: Float? = null
            if (stamps.size >= 30) {
                val iv = ArrayList<Long>(stamps.size)
                for (i in 1 until stamps.size) iv.add(stamps[i] - stamps[i - 1])
                iv.sortDescending()
                val n = maxOf(1, iv.size / 100)
                var sum = 0L
                for (i in 0 until n) sum += iv[i]
                val avgNs = sum.toFloat() / n
                if (avgNs > 0f) low = 1_000_000_000f / avgNs
            }
            val ft = if (fps > 0.5f) 1000f / fps else 0f
            return FpsSnapshot(fps, ft, low, frames)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try { projection.unregisterCallback(callback) } catch (_: Exception) {}
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { reader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection.stop() } catch (_: Exception) {}
        thread?.quitSafely()
        thread = null
    }
}
