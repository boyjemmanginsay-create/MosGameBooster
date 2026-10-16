package com.mos.gamebooster

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

data class AppItem(val label: String, val pkg: String)

object Booster {
    private val NEVER = setOf("moe.shizuku.privileged.api", "com.mos.gamebooster")

    fun freeRamMb(ctx: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.availMem / 1048576
    }

    fun installed(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != ctx.packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    private fun homePkg(ctx: Context): String? =
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    fun boost(ctx: Context, st: Store, game: String?): String {
        val sb = StringBuilder()
        val before = freeRamMb(ctx)
        fun r(label: String, cmd: String) {
            val o = Sh.run(cmd).trim()
            val ok = o.isEmpty() || o.contains("new state") || o.contains("Success") || o.contains("ok", true)
            sb.append(if (ok) "✓ $label\n" else "✗ $label → ${o.take(70)}\n")
        }
        val keep = NEVER + st.set("white") + setOfNotNull(game, homePkg(ctx),
            Sh.run("settings get secure default_input_method").trim().substringBefore("/"))

        // 1. Freeze selected apps
        val frozen = st.set("freeze") - keep
        frozen.forEach { r("Freeze $it", "pm disable-user --user 0 $it") }
        st.put("frozenNow", st.set("frozenNow") + frozen)

        // 2. Force stop all third-party apps (except whitelist/game/launcher/keyboard)
        val pkgs = Sh.run("pm list packages -3").lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
        val victims = pkgs.filter { it !in keep }
        victims.forEach { Sh.run("am force-stop $it") }
        sb.append("✓ Force stopped ${victims.size} apps\n")
        Sh.run("am kill-all")

        // 3. Clear caches
        r("Clear all app cache", "pm trim-caches 999G")

        // 4. System tweaks
        if (st.flag("anim", true)) listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .forEach { Sh.run("settings put global $it 0") }.also { sb.append("✓ Animations off\n") }
        if (st.flag("dnd", true)) r("Do Not Disturb ON", "cmd notification set_dnd priority")
        if (st.flag("perf", true)) {
            r("Battery saver off", "settings put global low_power 0")
            r("Fixed performance mode", "cmd power set-fixed-performance-mode-enabled true")
        }

        // 5. Per-game graphics (Game Mode, Android 12+/13+)
        if (game != null) {
            val c = st.cfg(game)
            val fps = if (c.fps > 0) "--fps ${c.fps} " else ""
            r("Game profile ${c.mode} • ${String.format(Locale.US, "%.0f", c.down * 100)}% res",
                "cmd game set --mode ${c.mode} --downscale ${String.format(Locale.US, "%.2f", c.down)} $fps$game")
        }
        Thread.sleep(800)
        val after = freeRamMb(ctx)
        sb.append("\nRAM: $before MB → $after MB (+${after - before} MB)")
        return sb.toString()
    }

    fun restore(ctx: Context, st: Store): String {
        val sb = StringBuilder()
        st.set("frozenNow").forEach { Sh.run("pm enable $it") }
        sb.append("✓ Unfroze ${st.set("frozenNow").size} apps\n"); st.put("frozenNow", emptySet())
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { Sh.run("settings put global $it 1") }
        Sh.run("cmd notification set_dnd off")
        Sh.run("cmd power set-fixed-performance-mode-enabled false")
        st.set("games").forEach { Sh.run("cmd game reset $it") }
        sb.append("✓ Animations, DND, performance mode & game profiles restored")
        return sb.toString()
    }
}
