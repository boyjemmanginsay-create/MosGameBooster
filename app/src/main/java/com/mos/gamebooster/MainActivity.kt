package com.mos.gamebooster

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sh.init()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00E5A0), background = Color(0xFF0B0F14), surface = Color(0xFF141B23))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
    override fun onDestroy() { Sh.release(); super.onDestroy() }
}

@Composable fun App() {
    val ctxApp = LocalContext.current
    val st = remember { Store(ctxApp) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Boost", "Games", "Tools"); val icons = listOf("⚡", "🎮", "🛠")
    Scaffold(containerColor = Color.Transparent, bottomBar = {
        NavigationBar {
            names.forEachIndexed { i, n ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icons[i]) }, label = { Text(n) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> Home(st); 1 -> Games(st); else -> Tools(st) }
        }
    }
}

@Composable fun Home(st: Store) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var ram by remember { mutableLongStateOf(Booster.freeRamMb(ctx)) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mos Game Booster V1", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(Sh.status.value)
        if (!Sh.ready.value) Button(onClick = { Sh.check() }) { Text("Connect Shizuku") }
        Text("Free RAM: $ram MB", fontSize = 18.sp)
        Button(enabled = Sh.ready.value && !busy, modifier = Modifier.fillMaxWidth().height(64.dp), onClick = {
            scope.launch { busy = true; log = "Boosting…"
                log = withContext(Dispatchers.IO) { Booster.boost(ctx, st, null) }
                ram = Booster.freeRamMb(ctx); busy = false }
        }) { Text("⚡ BOOST NOW", fontSize = 20.sp) }
        OutlinedButton(enabled = Sh.ready.value && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch { log = withContext(Dispatchers.IO) { Booster.restore(ctx, st) } }
        }) { Text("Restore everything to normal") }
        Text(log, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable fun AppRow(a: AppItem, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column { Text(a.label); Text(a.pkg, fontSize = 10.sp, color = Color.Gray) }
    }
}

@Composable fun rememberApps(): List<AppItem> {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppItem>()) { value = withContext(Dispatchers.IO) { Booster.installed(ctx) } }
    return apps
}

@Composable fun Games(st: Store) {
    val apps = rememberApps()
    var games by remember { mutableStateOf(st.set("games")) }
    var picking by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Game Panel", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { picking = !picking }) { Text(if (picking) "Done" else "+ Add / remove games") }
        }
        if (picking) items(apps) { a ->
            AppRow(a, a.pkg in games) { games = if (a.pkg in games) games - a.pkg else games + a.pkg; st.put("games", games) }
        } else items(apps.filter { it.pkg in games }) { GameCard(it, st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GameCard(a: AppItem, st: Store) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var cfg by remember(a.pkg) { mutableStateOf(st.cfg(a.pkg)) }
    var out by remember { mutableStateOf("") }
    fun upd(c: GameCfg) { cfg = c; st.saveCfg(a.pkg, c) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(a.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(false, { upd(GameCfg("performance", 0.8f, 0)) }, { Text("Balanced") })
                FilterChip(false, { upd(GameCfg("performance", 0.7f, 60)) }, { Text("Perf") })
                FilterChip(false, { upd(GameCfg("battery", 0.4f, 30)) }, { Text("Potato 🥔") })
            }
            Text("Resolution: ${String.format(Locale.US, "%.0f", cfg.down * 100)}%  •  Mode: ${cfg.mode}  •  FPS: ${if (cfg.fps == 0) "default" else cfg.fps}")
            Slider(cfg.down, { upd(cfg.copy(down = it)) }, valueRange = 0.3f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 30, 60, 90, 120).forEach { f ->
                    FilterChip(cfg.fps == f, { upd(cfg.copy(fps = f)) }, { Text(if (f == 0) "Auto" else "$f") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("standard", "performance", "battery").forEach { m ->
                    FilterChip(cfg.mode == m, { upd(cfg.copy(mode = m)) }, { Text(m) })
                }
            }
            Button(enabled = Sh.ready.value, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    out = withContext(Dispatchers.IO) { Booster.boost(ctx, st, a.pkg) }
                    ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it) }
                }
            }) { Text("🚀 Boost & Launch") }
            if (out.isNotEmpty()) Text(out, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable fun Tools(st: Store) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val apps = rememberApps()
    var anim by remember { mutableStateOf(st.flag("anim", true)) }
    var dnd by remember { mutableStateOf(st.flag("dnd", true)) }
    var perf by remember { mutableStateOf(st.flag("perf", true)) }
    var freeze by remember { mutableStateOf(st.set("freeze")) }
    var white by remember { mutableStateOf(st.set("white")) }
    @Composable fun sw(label: String, v: Boolean, key: String, set: (Boolean) -> Unit) =
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label); Switch(v, { set(it); st.putFlag(key, it) })
        }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Tools", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            sw("Animations off (faster feel)", anim, "anim") { anim = it }
            sw("Do Not Disturb while boosting", dnd, "dnd") { dnd = it }
            sw("Performance mode + battery saver off", perf, "perf") { perf = it }
            OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { Booster.restore(ctx, st) } }) { Text("Unfreeze all / restore") }
            Spacer(Modifier.height(8.dp))
            Text("❄ Freeze list (disabled during boost)", fontWeight = FontWeight.Bold)
        }
        items(apps) { a -> AppRow(a, a.pkg in freeze) { freeze = if (a.pkg in freeze) freeze - a.pkg else freeze + a.pkg; st.put("freeze", freeze) } }
        item { Spacer(Modifier.height(8.dp)); Text("🛡 Whitelist (never force-stopped)", fontWeight = FontWeight.Bold) }
        items(apps) { a -> AppRow(a, a.pkg in white) { white = if (a.pkg in white) white - a.pkg else white + a.pkg; st.put("white", white) } }
    }
}
