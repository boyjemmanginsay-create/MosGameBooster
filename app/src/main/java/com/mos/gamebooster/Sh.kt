package com.mos.gamebooster

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku

object Sh {
    private var svc: IUserService? = null
    val ready = mutableStateOf(false)
    val status = mutableStateOf("Checking Shizuku…")

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            svc = IUserService.Stub.asInterface(b); ready.value = true; status.value = "Shizuku connected ✓"
        }
        override fun onServiceDisconnected(n: ComponentName?) { svc = null; ready.value = false; status.value = "Service disconnected" }
    }
    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .daemon(false).processNameSuffix("svc").debuggable(BuildConfig.DEBUG).version(1)
    }
    private val onBinder = Shizuku.OnBinderReceivedListener { check() }
    private val onDead = Shizuku.OnBinderDeadListener { svc = null; ready.value = false; status.value = "Shizuku stopped. Start it first." }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, r -> if (r == PackageManager.PERMISSION_GRANTED) check() else status.value = "Permission denied" }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
        check()
    }
    fun release() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
    }
    fun check() {
        if (!Shizuku.pingBinder()) { status.value = "Shizuku not running. Open Shizuku & start it."; return }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (!ready.value) Shizuku.bindUserService(args, conn)
        } else Shizuku.requestPermission(100)
    }
    fun run(cmd: String): String = try { svc?.exec(cmd) ?: "ERR: no service" } catch (e: Exception) { "ERR: ${e.message}" }
}
