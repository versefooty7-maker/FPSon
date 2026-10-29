package com.fpspulse.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

object C {
    val bg = Color(0xFF0A0E1A)
    val card = Color(0xFF121829)
    val green = Color(0xFF00F5A0)
    val cyan = Color(0xFF00D4FF)
    val yellow = Color(0xFFFFD60A)
    val red = Color(0xFFFF3B5C)
    val gray = Color(0xFF8A93A8)
    val white = Color(0xFFF2F5FF)
    val dark = Color(0xFF00170E)
}

fun fpsColor(fps: Float, target: Int): Color = Color(NeonColors.forFps(fps, target))
fun tempColor(t: Float, warn: Int): Color = Color(NeonColors.forTemp(t, warn))

fun fmtTime(sec: Int): String = String.format("%02d:%02d", sec / 60, sec % 60)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF161D33), Color(0xFF10162A))))
            .border(1.dp, Color(0x1AFFFFFF), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = C.gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
fun GlowButton(text: String, running: Boolean, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "glow")
    val a by inf.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val base = if (running) C.red else C.green
    val end = if (running) Color(0xFFFF8A5C) else C.cyan
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(base, end)))
            .border(2.dp, base.copy(alpha = a), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (running) Color.White else C.dark,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF151C31))
            .border(1.dp, Color(0x14FFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun TileRow(
    aLabel: String, aValue: String, aColor: Color,
    bLabel: String, bValue: String, bColor: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(aLabel, aValue, aColor, Modifier.weight(1f))
        StatTile(bLabel, bValue, bColor, Modifier.weight(1f))
    }
}

@Composable
fun FpsGraph(values: List<Float>, target: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0x14FFFFFF), cornerRadius = CornerRadius(12.dp.toPx()))
        if (values.size < 2) return@Canvas
        val maxV = max(target.toFloat(), values.max())
        val line = Path()
        val fill = Path()
        val pad = 6.dp.toPx()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1).toFloat()
            val y = h - pad - (v / maxV).coerceIn(0f, 1f) * (h - pad * 2)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(w, h)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun StatusRow(title: String, subtitle: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) C.green else C.yellow))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = C.white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = C.gray, fontSize = 12.sp)
        }
        if (!ok && action != null) {
            Text(
                action,
                color = C.dark,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.green)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun MonitorScreen(
    overlayGranted: Boolean,
    notifGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotif: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var polled by remember { mutableStateOf(SystemStats.read(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            polled = SystemStats.read(ctx)
            delay(2000)
        }
    }

    val running = ServiceState.running
    val target = prefs.targetFps
    val fps = if (running) ServiceState.fps else 0f
    val s = if (running) (ServiceState.stats ?: polled) else polled

    val animFps by animateFloatAsState(fps, tween(450), label = "fps")
    val numColor by animateColorAsState(
        if (running) fpsColor(fps, target) else C.gray, tween(450), label = "fpsColor"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FPS PULSE", color = C.white, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (running) "LIVE" else "OFFLINE",
                color = if (running) C.dark else C.gray,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (running) C.green else Color(0xFF1A2238))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Panel {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (running) animFps.toInt().toString() else "--",
                    color = numColor,
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "FPS", color = C.gray, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 18.dp)
                )
            }
            Text(
                if (running) "Measured from real screen frames"
                else "Press START to measure the real FPS of whatever is on screen",
                color = C.gray, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            FpsGraph(
                values = ServiceState.history,
                target = target,
                color = numColor,
                modifier = Modifier.fillMaxWidth().height(90.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    "FRAME TIME",
                    if (running && ServiceState.frameMs > 0f) String.format("%.1f ms", ServiceState.frameMs) else "--",
                    C.white, Modifier.weight(1f)
                )
                StatTile(
                    "1% LOW",
                    ServiceState.low1?.let { if (running) it.toInt().toString() else "--" } ?: "--",
                    C.cyan, Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("AVG", if (running) "${ServiceState.sessionAvg}" else "--", C.white, Modifier.weight(1f))
                StatTile("MAX", if (running) "${ServiceState.sessionMax}" else "--", C.green, Modifier.weight(1f))
                StatTile("MIN", if (running) "${ServiceState.sessionMin}" else "--", C.yellow, Modifier.weight(1f))
            }
            if (running) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Session ${fmtTime(ServiceState.sessionSeconds)}",
                    color = C.gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlowButton(
            text = if (running) "STOP MONITOR" else "START MONITOR",
            running = running,
            onClick = { if (running) onStop() else onStart() }
        )

        SectionTitle("Device")
        val sign = if (s.charging) "+" else "-"
        TileRow(
            "BATTERY", "${s.batteryPct}%" + if (s.charging) " charging" else "",
            if (s.batteryPct > 30) C.green else if (s.batteryPct > 15) C.yellow else C.red,
            "CURRENT", "$sign${s.currentMa} mA", if (s.charging) C.cyan else C.white
        )
        TileRow(
            "BATTERY TEMP", String.format("%.1f °C", s.batteryTempC), tempColor(s.batteryTempC, prefs.heatWarnC),
            "CPU TEMP", s.cpuTempC?.let { String.format("%.0f °C", it) } ?: "N/A",
            s.cpuTempC?.let { tempColor(it, prefs.heatWarnC + 20) } ?: C.gray
        )
        TileRow(
            "RAM USED", "${s.ramUsedPct}%",
            if (s.ramUsedPct < 70) C.green else if (s.ramUsedPct < 85) C.yellow else C.red,
            "VOLTAGE", String.format("%.2f V", s.voltageV), C.white
        )

        SectionTitle("Setup")
        Panel {
            StatusRow(
                "Display over other apps", "Needed to draw the FPS overlay on top of games",
                overlayGranted, "GRANT", onGrantOverlay
            )
            StatusRow(
                "Notifications", "Shows the running monitor and a Stop button",
                notifGranted, "GRANT", onGrantNotif
            )
            StatusRow(
                "Screen capture", "Android asks each time you press START. Nothing is recorded or saved.",
                true, null, {}
            )
        }

        Panel {
            Text("How the FPS is measured", color = C.white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "The app mirrors the screen into a tiny hidden buffer and counts every frame the display really updates. " +
                        "No Shizuku, no root. A static screen shows 0 because nothing is being drawn. " +
                        "Readings can be off by about 1 FPS because the overlay itself refreshes once a second. " +
                        "If a game caps its own FPS, the number follows the cap.",
                color = C.gray, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
