package com.mos.gamebooster

import kotlin.system.exitProcess

/** Tumatakbo sa shell (ADB) privilege via Shizuku. */
class UserService : IUserService.Stub() {
    override fun destroy() { exitProcess(0) }
    override fun exec(cmd: String): String = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) { "ERR: ${e.message}" }
}
