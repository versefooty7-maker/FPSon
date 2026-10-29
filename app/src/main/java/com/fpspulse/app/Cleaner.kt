package com.fpspulse.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

data class GameApp(val label: String, val packageName: String)

data class CleanResult(val appsTouched: Int, val freedMb: Long, val cacheMb: Long)

object Cleaner {

    /** Launchable apps the system tags as games, plus any app the user pinned. */
    fun findGames(ctx: Context, pinned: Set<String>): List<GameApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<GameApp>()
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val info = ri.activityInfo?.applicationInfo ?: continue
            if (info.packageName == ctx.packageName) continue
            val isGame = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                    info.category == ApplicationInfo.CATEGORY_GAME
            if (isGame || pinned.contains(info.packageName)) {
                out.add(GameApp(ri.loadLabel(pm).toString(), info.packageName))
            }
        }
        return out.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun allLaunchable(ctx: Context): List<GameApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { GameApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, packageName: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }

    /**
     * RAM boost: asks Android to stop background processes of other apps.
     * Android decides what actually gets stopped; foreground apps and system apps are untouched.
     */
    fun boostRam(ctx: Context): CleanResult {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = freeMb(am)
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var touched = 0
        for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            try {
                am.killBackgroundProcesses(pkg)
                touched++
            } catch (_: Exception) {
            }
        }
        try { Thread.sleep(700) } catch (_: InterruptedException) {}
        val after = freeMb(am)
        return CleanResult(touched, (after - before).coerceAtLeast(0), 0)
    }

    /** Clears this app's own cache and session leftovers. Other apps' caches are protected by Android. */
    fun clearOwnCache(ctx: Context): Long {
        var bytes = 0L
        fun walk(f: File) {
            if (f.isDirectory) f.listFiles()?.forEach { walk(it) }
            bytes += f.length()
            f.delete()
        }
        ctx.cacheDir?.listFiles()?.forEach { walk(it) }
        ctx.externalCacheDir?.listFiles()?.forEach { walk(it) }
        return bytes / (1024 * 1024)
    }

    private fun freeMb(am: ActivityManager): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }
}
