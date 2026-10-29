package com.fpspulse.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.min

object NeonColors {
    const val GREEN = 0xFF00F5A0.toInt()
    const val YELLOW = 0xFFFFD60A.toInt()
    const val RED = 0xFFFF3B5C.toInt()
    const val CYAN = 0xFF00D4FF.toInt()
    const val GRAY = 0xFF8A93A8.toInt()
    const val WHITE = 0xFFF2F5FF.toInt()
    const val BG = 0xFF0A0E1A.toInt()

    fun forFps(fps: Float, target: Int): Int {
        val t = max(1, target).toFloat()
        return when {
            fps >= t * 0.9f -> GREEN
            fps >= t * 0.6f -> YELLOW
            else -> RED
        }
    }

    fun forTemp(tempC: Float, warn: Int): Int = when {
        tempC >= warn -> RED
        tempC >= warn - 5 -> YELLOW
        else -> GREEN
    }
}

private data class Item(val label: String, val value: String, val color: Int)

/** Floating HUD. Everything is drawn in dp units and scaled, so size follows the scale setting. */
class OverlayView(context: Context, private val prefs: Prefs) : View(context) {

    private val density = resources.displayMetrics.density

    private var fps = 0f
    private var frameMs = 0f
    private var low1: Float? = null
    private var stats: Stats? = null
    private var hasData = false

    private val history = FloatArray(60)
    private var historyCount = 0

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 38f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val path = Path()
    private val fillPath = Path()

    fun update(snapshot: FpsSnapshot, s: Stats) {
        fps = snapshot.fps
        frameMs = snapshot.frameTimeMs
        low1 = snapshot.low1Fps
        stats = s
        hasData = true
        for (i in 0 until history.size - 1) history[i] = history[i + 1]
        history[history.size - 1] = fps
        if (historyCount < history.size) historyCount++
        invalidate()
    }

    fun updateStatsOnly(s: Stats) {
        stats = s
        invalidate()
    }

    private fun items(): List<Item> {
        val s = stats ?: return emptyList()
        val out = ArrayList<Item>()
        if (prefs.showBattery) {
            val c = when {
                s.batteryPct > 30 -> NeonColors.GREEN
                s.batteryPct > 15 -> NeonColors.YELLOW
                else -> NeonColors.RED
            }
            out.add(Item("BAT", "${s.batteryPct}%", c))
            val sign = if (s.charging) "+" else "-"
            out.add(Item("CUR", "$sign${s.currentMa}mA", if (s.charging) NeonColors.CYAN else NeonColors.WHITE))
        }
        if (prefs.showTemp) {
            out.add(Item("TMP", String.format("%.1f°C", s.batteryTempC), NeonColors.forTemp(s.batteryTempC, prefs.heatWarnC)))
            val cpu = s.cpuTempC
            if (cpu != null) {
                out.add(Item("CPU", String.format("%.0f°C", cpu), NeonColors.forTemp(cpu, prefs.heatWarnC + 20)))
            }
        }
        if (prefs.showRam) {
            val c = when {
                s.ramUsedPct < 70 -> NeonColors.GREEN
                s.ramUsedPct < 85 -> NeonColors.YELLOW
                else -> NeonColors.RED
            }
            out.add(Item("RAM", "${s.ramUsedPct}%", c))
        }
        return out
    }

    private fun widthDp(): Float = if (prefs.compact) 104f else 176f

    private fun heightDp(): Float {
        if (prefs.compact) return 40f
        val start = if (prefs.showGraph) 102f else 66f
        val rows = (items().size + 1) / 2
        return if (rows == 0) start - 4f else start + rows * 18f + 6f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val k = density * prefs.scale
        setMeasuredDimension((widthDp() * k).toInt(), (heightDp() * k).toInt())
    }

    private fun isHot(): Boolean {
        val s = stats ?: return false
        return s.batteryTempC >= prefs.heatWarnC
    }

    override fun onDraw(canvas: Canvas) {
        val k = density * prefs.scale
        val w = widthDp()
        val h = heightDp()
        canvas.save()
        canvas.scale(k, k)

        val hot = isHot()
        val numColor = if (hasData) NeonColors.forFps(fps, prefs.targetFps) else NeonColors.GRAY
        val accent = if (hot) NeonColors.RED else numColor

        bgPaint.color = 0xE60A0E1A.toInt()
        rect.set(0.75f, 0.75f, w - 0.75f, h - 0.75f)
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
        borderPaint.color = (accent and 0x00FFFFFF) or (0xB0 shl 24)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        val fpsText = if (hasData) fps.toInt().toString() else "--"

        if (prefs.compact) {
            bigPaint.textSize = 24f
            bigPaint.color = numColor
            canvas.drawText(fpsText, 12f, 29f, bigPaint)
            val tw = bigPaint.measureText(fpsText)
            textPaint.textSize = 10f
            textPaint.color = NeonColors.GRAY
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("FPS", 12f + tw + 4f, 29f, textPaint)
            canvas.restore()
            return
        }

        // Big FPS number
        bigPaint.textSize = 38f
        bigPaint.color = numColor
        canvas.drawText(fpsText, 12f, 44f, bigPaint)
        val numW = bigPaint.measureText(fpsText)
        textPaint.textSize = 11f
        textPaint.color = NeonColors.GRAY
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("FPS", 12f + numW + 4f, 44f, textPaint)

        // Right column: frame time and 1% low
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 12f
        textPaint.color = NeonColors.WHITE
        val ftText = if (hasData && frameMs > 0f) String.format("%.1f ms", frameMs) else "-- ms"
        canvas.drawText(ftText, w - 12f, 26f, textPaint)
        val l = low1
        if (prefs.showLow1) {
            textPaint.textSize = 10f
            textPaint.color = NeonColors.GRAY
            val lowText = if (l != null) "1% low ${l.toInt()}" else "1% low --"
            canvas.drawText(lowText, w - 12f, 42f, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT

        // Target bar
        barPaint.color = 0x22FFFFFF
        rect.set(12f, 52f, w - 12f, 55f)
        canvas.drawRoundRect(rect, 1.5f, 1.5f, barPaint)
        val frac = if (hasData) min(1f, fps / max(1, prefs.targetFps)) else 0f
        barPaint.color = numColor
        rect.set(12f, 52f, 12f + (w - 24f) * frac, 55f)
        canvas.drawRoundRect(rect, 1.5f, 1.5f, barPaint)

        // Graph
        var y = 64f
        if (prefs.showGraph) {
            val gx = 12f
            val gw = w - 24f
            val gh = 32f
            var maxV = prefs.targetFps.toFloat()
            for (v in history) if (v > maxV) maxV = v
            barPaint.color = 0x14FFFFFF
            rect.set(gx, y, gx + gw, y + gh)
            canvas.drawRoundRect(rect, 4f, 4f, barPaint)

            if (historyCount >= 2) {
                path.reset()
                fillPath.reset()
                val n = history.size
                val startIdx = n - historyCount
                for (i in startIdx until n) {
                    val px = gx + gw * (i.toFloat() / (n - 1))
                    val py = y + gh - (history[i] / maxV).coerceIn(0f, 1f) * (gh - 2f) - 1f
                    if (i == startIdx) {
                        path.moveTo(px, py)
                        fillPath.moveTo(px, y + gh)
                        fillPath.lineTo(px, py)
                    } else {
                        path.lineTo(px, py)
                        fillPath.lineTo(px, py)
                    }
                }
                fillPath.lineTo(gx + gw, y + gh)
                fillPath.close()
                fillPaint.color = (numColor and 0x00FFFFFF) or (0x33 shl 24)
                canvas.drawPath(fillPath, fillPaint)
                linePaint.color = numColor
                canvas.drawPath(path, linePaint)
            }
            y += gh + 6f
        }

        // Info grid (2 columns)
        val list = items()
        textPaint.textAlign = Paint.Align.LEFT
        for ((i, item) in list.withIndex()) {
            val col = i % 2
            val row = i / 2
            val x = if (col == 0) 12f else w / 2f + 2f
            val by = y + 12f + row * 18f
            textPaint.textSize = 9f
            textPaint.color = NeonColors.GRAY
            canvas.drawText(item.label, x, by, textPaint)
            textPaint.textSize = 11.5f
            textPaint.color = item.color
            canvas.drawText(item.value, x + 26f, by, textPaint)
        }

        canvas.restore()
    }
}
