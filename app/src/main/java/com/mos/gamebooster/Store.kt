package com.mos.gamebooster

import android.content.Context
import java.util.Locale

data class GameCfg(val mode: String = "performance", val down: Float = 0.8f, val fps: Int = 0)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("mos", 0)
    fun set(k: String): Set<String> = p.getStringSet(k, emptySet())!!.toSet()
    fun put(k: String, v: Set<String>) = p.edit().putStringSet(k, v).apply()
    fun flag(k: String, def: Boolean = false) = p.getBoolean(k, def)
    fun putFlag(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    fun cfg(pkg: String): GameCfg {
        val s = p.getString("cfg_$pkg", null)?.split(",") ?: return GameCfg()
        return GameCfg(s[0], s[1].toFloat(), s[2].toInt())
    }
    fun saveCfg(pkg: String, c: GameCfg) =
        p.edit().putString("cfg_$pkg", "${c.mode},${String.format(Locale.US, "%.2f", c.down)},${c.fps}").apply()
}
