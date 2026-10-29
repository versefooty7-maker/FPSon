package com.fpspulse.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private val Bg = Color(0xFF0A0E1A)
private val Surface = Color(0xFF121829)
private val Green = Color(0xFF00F5A0)

private val FpsColorScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF00170E),
    secondary = Color(0xFF00D4FF),
    background = Bg,
    onBackground = Color(0xFFF2F5FF),
    surface = Surface,
    onSurface = Color(0xFFF2F5FF),
    surfaceVariant = Color(0xFF1A2238),
    onSurfaceVariant = Color(0xFF8A93A8),
    error = Color(0xFFFF3B5C)
)

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    // Updated in onResume so the UI always shows the real permission state.
    private var overlayGranted by mutableStateOf(false)
    private var notifGranted by mutableStateOf(true)

    private var pendingLabel: String? = null
    private var pendingLaunchPkg: String? = null

    private lateinit var captureLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var notifLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK && res.data != null) {
                val i = Intent(this, FpsService::class.java)
                    .setAction(FpsService.ACTION_START)
                    .putExtra(FpsService.EXTRA_RESULT_CODE, res.resultCode)
                    .putExtra(FpsService.EXTRA_DATA, res.data)
                    .putExtra(FpsService.EXTRA_APP_LABEL, pendingLabel ?: "Manual session")
                startForegroundService(i)
                pendingLaunchPkg?.let { pkg ->
                    if (!Cleaner.launch(this, pkg)) {
                        Toast.makeText(this, "Could not open that app", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Screen capture permission is needed to read the real FPS", Toast.LENGTH_LONG).show()
            }
            pendingLaunchPkg = null
        }

        notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notifGranted = it
            requestCapture()
        }

        setContent {
            MaterialTheme(colorScheme = FpsColorScheme) {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
        notifGranted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Entry point for every "start" button. [label] names the session, [launchPkg] opens a game afterwards. */
    fun startMonitoring(label: String?, launchPkg: String?) {
        if (ServiceState.running) {
            // Already running: just open the game.
            launchPkg?.let { Cleaner.launch(this, it) }
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" for FPS Pulse, then press Start again", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        pendingLabel = label
        pendingLaunchPkg = launchPkg
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestCapture()
        }
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    fun stopMonitoring() {
        startService(Intent(this, FpsService::class.java).setAction(FpsService.ACTION_STOP))
    }

    fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    fun openNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Composable
    private fun AppRoot() {
        var tab by remember { mutableStateOf(0) }
        val tabs: List<Pair<String, ImageVector>> = listOf(
            "Monitor" to Icons.Filled.Speed,
            "Games" to Icons.Filled.SportsEsports,
            "Boost" to Icons.Filled.Bolt,
            "History" to Icons.Filled.History,
            "Settings" to Icons.Filled.Settings
        )
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Surface) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.second, contentDescription = t.first) },
                            label = { Text(t.first) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00170E),
                                selectedTextColor = Green,
                                indicatorColor = Green,
                                unselectedIconColor = Color(0xFF8A93A8),
                                unselectedTextColor = Color(0xFF8A93A8)
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> MonitorScreen(
                        overlayGranted = overlayGranted,
                        notifGranted = notifGranted,
                        onStart = { startMonitoring(null, null) },
                        onStop = { stopMonitoring() },
                        onGrantOverlay = { openOverlaySettings() },
                        onGrantNotif = { openNotificationPermission() }
                    )
                    1 -> GamesScreen(
                        prefs = prefs,
                        onPlay = { g -> startMonitoring(g.label, g.packageName) }
                    )
                    2 -> BoostScreen()
                    3 -> HistoryScreen(prefs)
                    else -> SettingsScreen(prefs)
                }
            }
        }
    }
}
