package com.fpspulse.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ----------------------------- GAMES ----------------------------- */

@Composable
fun GamesScreen(prefs: Prefs, onPlay: (GameApp) -> Unit) {
    val ctx = LocalContext.current
    var pinned by remember { mutableStateOf(prefs.pinned) }
    var games by remember { mutableStateOf(Cleaner.findGames(ctx, pinned)) }
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GAMES", color = C.white, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "+ ADD APP", color = C.dark, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.green)
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Text(
            "Tap PLAY to start the FPS overlay and open the game in one step.",
            color = C.gray, fontSize = 12.sp
        )

        if (games.isEmpty()) {
            Panel {
                Text("No games found", color = C.white, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android did not tag any installed app as a game. Use ADD APP to pick the games you play.",
                    color = C.gray, fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(games, key = { it.packageName }) { g ->
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xFF151C31))
                            .border(1.dp, Color(0x14FFFFFF), shape)
                            .padding(14.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.label, color = C.white, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text(g.packageName, color = C.gray, fontSize = 10.sp, maxLines = 1)
                        }
                        if (pinned.contains(g.packageName)) {
                            Text(
                                "REMOVE", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        pinned = pinned - g.packageName
                                        prefs.pinned = pinned
                                        games = Cleaner.findGames(ctx, pinned)
                                    }
                                    .padding(8.dp)
                            )
                        }
                        Text(
                            "PLAY", color = C.dark, fontWeight = FontWeight.Black, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.green)
                                .clickable { onPlay(g) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        val all = remember { Cleaner.allLaunchable(ctx) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = C.card,
            title = { Text("Add an app", color = C.white) },
            text = {
                LazyColumn(Modifier.height(380.dp)) {
                    items(all, key = { it.packageName }) { a ->
                        val on = pinned.contains(a.packageName)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pinned = if (on) pinned - a.packageName else pinned + a.packageName
                                    prefs.pinned = pinned
                                    games = Cleaner.findGames(ctx, pinned)
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(a.label, color = C.white, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(if (on) "ADDED" else "ADD", color = if (on) C.green else C.gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Done", color = C.green) } }
        )
    }
}

/* ----------------------------- BOOST ----------------------------- */

@Composable
fun BoostScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var stats by remember { mutableStateOf(SystemStats.read(ctx)) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            stats = SystemStats.read(ctx)
            delay(2000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BOOST", color = C.white, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)

        Panel {
            Text("MEMORY", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${stats.ramUsedPct}% used",
                color = if (stats.ramUsedPct < 70) C.green else if (stats.ramUsedPct < 85) C.yellow else C.red,
                fontSize = 30.sp, fontWeight = FontWeight.Black
            )
            Text("${stats.ramFreeMb} MB free of ${stats.ramTotalMb} MB", color = C.gray, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(stats.ramUsedPct.coerceIn(1, 100) / 100f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF00D4FF))
                )
            }
            Spacer(Modifier.height(14.dp))
            GlowButton(if (busy) "WORKING..." else "BOOST RAM", running = false) {
                if (!busy) {
                    busy = true
                    Thread {
                        val r = Cleaner.boostRam(ctx)
                        val msg = "Asked Android to stop background apps (${r.appsTouched} checked). " +
                                if (r.freedMb > 0) "About ${r.freedMb} MB freed." else "Memory was already clean."
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            result = msg
                            stats = SystemStats.read(ctx)
                            busy = false
                        }
                    }.start()
                }
            }
            result?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = C.green, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Panel {
            Text("HEAT", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            val t = stats.batteryTempC
            Text(String.format("%.1f °C", t), color = tempColor(t, prefs.heatWarnC), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(
                when {
                    t >= prefs.heatWarnC -> "Too hot. Take a break, remove the case, close heavy apps."
                    t >= prefs.heatWarnC - 5 -> "Getting warm. Performance may drop soon."
                    else -> "Temperature is fine."
                },
                color = C.gray, fontSize = 12.sp
            )
        }

        Panel {
            Text("CLEAN", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            ToolButton("Clear FPS Pulse cache") {
                val mb = Cleaner.clearOwnCache(ctx)
                result = "FPS Pulse cache cleared (${mb} MB)."
            }
            Spacer(Modifier.height(8.dp))
            ToolButton("Open storage cleaner (system)") {
                openSettings(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            }
            Spacer(Modifier.height(8.dp))
            ToolButton("Open battery saver settings") {
                openSettings(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Android does not allow any app to delete other apps' cache directly, so those buttons open the system cleaner.",
                color = C.gray, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ToolButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF1A2238))
            .border(1.dp, Color(0x1AFFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(text, color = C.white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun openSettings(ctx: android.content.Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

/* ----------------------------- HISTORY ----------------------------- */

@Composable
fun HistoryScreen(prefs: Prefs) {
    var list by remember { mutableStateOf(prefs.sessions()) }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HISTORY", color = C.white, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            if (list.isNotEmpty()) {
                Text(
                    "CLEAR", color = C.gray, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { prefs.clearSessions(); list = emptyList() }
                        .padding(8.dp)
                )
            }
        }
        if (list.isEmpty()) {
            Panel {
                Text("No sessions yet", color = C.white, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every monitor session longer than 5 seconds is saved here with min, average and max FPS.",
                    color = C.gray, fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { s ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.app, color = C.white, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${s.avgFps}", color = C.green, fontWeight = FontWeight.Black, fontSize = 26.sp)
                            Text(" avg", color = C.gray, fontSize = 11.sp)
                        }
                        Text(
                            fmt.format(Date(s.startMs)) + "  ·  " + fmtTime(s.durationSec),
                            color = C.gray, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatTile("MIN", "${s.minFps}", C.yellow, Modifier.weight(1f))
                            StatTile("MAX", "${s.maxFps}", C.green, Modifier.weight(1f))
                            StatTile("1% LOW", "${s.low1Fps}", C.cyan, Modifier.weight(1f))
                            StatTile("TEMP", String.format("%.0f°", s.maxTempC), tempColor(s.maxTempC, prefs.heatWarnC), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------- SETTINGS ----------------------------- */

@Composable
fun SettingsScreen(prefs: Prefs) {
    var compact by remember { mutableStateOf(prefs.compact) }
    var graph by remember { mutableStateOf(prefs.showGraph) }
    var battery by remember { mutableStateOf(prefs.showBattery) }
    var temp by remember { mutableStateOf(prefs.showTemp) }
    var ram by remember { mutableStateOf(prefs.showRam) }
    var low1 by remember { mutableStateOf(prefs.showLow1) }
    var opacity by remember { mutableStateOf(prefs.opacity) }
    var scale by remember { mutableStateOf(prefs.scale) }
    var heat by remember { mutableStateOf(prefs.heatWarnC.toFloat()) }
    var target by remember { mutableStateOf(prefs.targetFps) }
    var posReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SETTINGS", color = C.white, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Changes apply to the running overlay within a second.", color = C.gray, fontSize = 12.sp)

        Panel {
            Text("OVERLAY CONTENT", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            SwitchRow("Compact mode (FPS only)", compact) { compact = it; prefs.compact = it }
            SwitchRow("FPS graph", graph) { graph = it; prefs.showGraph = it }
            SwitchRow("1% low", low1) { low1 = it; prefs.showLow1 = it }
            SwitchRow("Battery and current", battery) { battery = it; prefs.showBattery = it }
            SwitchRow("Temperature", temp) { temp = it; prefs.showTemp = it }
            SwitchRow("RAM usage", ram) { ram = it; prefs.showRam = it }
        }

        Panel {
            Text("LOOK", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Opacity ${(opacity * 100).toInt()}%", color = C.white, fontSize = 13.sp)
            Slider(
                value = opacity, onValueChange = { opacity = it; prefs.opacity = it },
                valueRange = 0.3f..1f, colors = sliderColors()
            )
            Text("Size ${(scale * 100).toInt()}%", color = C.white, fontSize = 13.sp)
            Slider(
                value = scale, onValueChange = { scale = it; prefs.scale = it },
                valueRange = 0.7f..1.6f, colors = sliderColors()
            )
            Spacer(Modifier.height(4.dp))
            ToolButton(if (posReset) "Position reset. Restart the monitor to apply" else "Reset overlay position") {
                prefs.resetPosition()
                posReset = true
            }
        }

        Panel {
            Text("TARGET FPS (colours the number)", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (t in listOf(30, 60, 90, 120)) {
                    val sel = target == t
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) C.green else Color(0xFF1A2238))
                            .clickable { target = t; prefs.targetFps = t }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$t", color = if (sel) C.dark else C.white, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Panel {
            Text("HEAT WARNING", color = C.gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Overlay turns red at ${heat.toInt()} °C", color = C.white, fontSize = 13.sp)
            Slider(
                value = heat, onValueChange = { heat = it; prefs.heatWarnC = it.toInt() },
                valueRange = 35f..50f, steps = 14, colors = sliderColors()
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, color = C.white, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.dark, checkedTrackColor = C.green,
                uncheckedThumbColor = C.gray, uncheckedTrackColor = Color(0xFF1A2238)
            )
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = C.green, activeTrackColor = C.green, inactiveTrackColor = Color(0xFF1A2238)
)
