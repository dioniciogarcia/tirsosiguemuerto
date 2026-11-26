package com.workspacehelper.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// -------- data access --------
object Repo {
    lateinit var prefs: SharedPreferences
    fun url() = prefs.getString("url", "") ?: ""
    fun secret() = prefs.getString("secret", "") ?: ""
    fun deviceId() = prefs.getString("deviceId", "") ?: ""
    private fun fb(path: String) = Net.fbUrl(url(), path, secret())

    suspend fun getStatus(): JSONObject? = withContext(Dispatchers.IO) {
        val r = Net.get(fb("devices/${deviceId()}/status")) ?: return@withContext null
        try { if (r == "null") null else JSONObject(r) } catch (e: Exception) { null }
    }

    suspend fun getScreenshot(): String? = withContext(Dispatchers.IO) {
        val r = Net.get(fb("devices/${deviceId()}/status/screenshot")) ?: return@withContext null
        try { if (r == "null") null else JSONObject(r).optString("jpg_b64", null) } catch (e: Exception) { null }
    }

    suspend fun listDevices(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val r = Net.get(fb("devices")) ?: return@withContext emptyList()
        try {
            val o = JSONObject(r)
            val out = mutableListOf<Pair<String, String>>()
            o.keys().forEach { id ->
                val name = o.optJSONObject(id)?.optJSONObject("status")?.optString("device_name", id) ?: id
                out.add(id to name)
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    suspend fun send(type: String, args: String = "{}"): String? = withContext(Dispatchers.IO) {
        val body = """{"type":"$type","args":$args,"ts":${System.currentTimeMillis()}}"""
        val r = Net.post(fb("devices/${deviceId()}/commands"), body) ?: return@withContext null
        try { JSONObject(r).optString("name", null) } catch (e: Exception) { null }
    }

    suspend fun getAck(): JSONObject? = withContext(Dispatchers.IO) {
        val r = Net.get(fb("devices/${deviceId()}/last_ack")) ?: return@withContext null
        try { if (r == "null") null else JSONObject(r) } catch (e: Exception) { null }
    }
}

fun hms(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.prefs = getSharedPreferences("wh", Context.MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(Repo.prefs.getString("theme", "dark") != "light") }
            WhTheme(dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(dark) { v ->
                        dark = v
                        Repo.prefs.edit().putString("theme", if (v) "dark" else "light").apply()
                    }
                }
            }
        }
    }
}

@Composable
fun AppRoot(dark: Boolean, onTheme: (Boolean) -> Unit) {
    var configured by remember {
        mutableStateOf(Repo.url().isNotEmpty() && Repo.secret().isNotEmpty())
    }
    if (!configured) { SetupScreen { configured = true }; return }

    var tab by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) { status = Repo.getStatus(); delay(4000) }
    }
    fun toast(m: String) = android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show()
    val cmd: (String, String) -> Unit = { type, args ->
        scope.launch {
            toast("Sent")
            val key = Repo.send(type, args)
            if (key != null) {
                repeat(6) {
                    delay(1500)
                    if (Repo.getAck()?.optString("key") == key) { toast("✓ Done"); return@launch }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.BarChart, null) }, label = { Text("Hours") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(status, cmd)
                1 -> HoursScreen(status)
                else -> SettingsScreen(dark, onTheme) { configured = false }
            }
        }
    }
}

// -------- Setup --------
@Composable
fun SetupScreen(onDone: () -> Unit) {
    var url by remember { mutableStateOf(Repo.url()) }
    var secret by remember { mutableStateOf(Repo.secret()) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("WorkspaceHelper", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Enter your Firebase connection to link to your PC.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Firebase URL") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(secret, { secret = it }, label = { Text("Firebase secret") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (url.isNotBlank() && secret.isNotBlank()) {
                    Repo.prefs.edit().putString("url", url.trim()).putString("secret", secret.trim()).apply()
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Connect") }
    }
}

// -------- Home --------
@Composable
fun HomeScreen(status: JSONObject?, cmd: (String, String) -> Unit) {
    val ts = status?.optLong("ts") ?: 0L
    val online = status?.optBoolean("online") == true &&
        (System.currentTimeMillis() / 1000.0 - ts < 30)
    val running = status?.optBoolean("desktime_running") == true
    val hours = status?.optJSONObject("hours")
    val total = hours?.optLong("desktimeTime") ?: 0
    val prod = hours?.optDouble("productivity") ?: 0.0
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C6CF6), Color(0xFF4F46E5)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("WorkspaceHelper", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusChip(if (online) "PC online" else "PC offline", if (online) Green else Rose)
        }
        Spacer(Modifier.height(12.dp))

        // hero status card (gradient)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))
                .padding(14.dp)
        ) {
            Text("DESKTIME", fontSize = 11.sp, letterSpacing = 1.5.sp, color = muted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (running) Green else muted)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Running" else "Stopped", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(hms(total), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("worked today", fontSize = 10.sp, color = muted)
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Productivity", fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
                Text("${prod.toInt()}%", fontSize = 12.sp, color = GreenText, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (prod / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                color = Green, trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("CONTROLS", fontSize = 11.sp, letterSpacing = 1.5.sp, color = muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        ControlCard(
            title = "DeskTime process", subtitle = "Open or close tracking",
            icon = Icons.Filled.PowerSettingsNew, iconBg = Color(0xFF201C33), iconTint = Violet2,
            onStart = { cmd("desktime_start", "{}") }, onStop = { cmd("desktime_stop", "{}") },
            onTimer = { m -> cmd("desktime_timer", """{"minutes":$m,"direction":"open_then_close"}""") }
        )
        Spacer(Modifier.height(9.dp))
        ControlCard(
            title = "Mouse helper · F9", subtitle = "Keeps the PC active",
            icon = Icons.Filled.Mouse, iconBg = Color(0xFF122A24), iconTint = Green,
            onStart = { cmd("mouse_start", """{"minutes":0}""") }, onStop = { cmd("mouse_stop", "{}") },
            onTimer = { m -> cmd("mouse_start", """{"minutes":$m}""") }
        )
        Spacer(Modifier.height(11.dp))

        ScreenshotButton()
        Spacer(Modifier.height(9.dp))
        Button(
            onClick = { cmd("panic", "{}") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1619), contentColor = Rose),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text("Panic · stop everything", fontWeight = FontWeight.SemiBold) }

        if (ts > 0) {
            val age = (System.currentTimeMillis() / 1000 - ts).coerceAtLeast(0)
            Spacer(Modifier.height(10.dp))
            Text(
                "Updated " + (if (age < 60) "${age}s" else "${age / 60}m") + " ago",
                fontSize = 11.sp, color = muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ControlCard(
    title: String, subtitle: String, icon: ImageVector, iconBg: Color, iconTint: Color,
    onStart: () -> Unit, onStop: () -> Unit, onTimer: (Int) -> Unit
) {
    var sel by remember { mutableStateOf(-1) }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onStart,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF10271F), contentColor = Green),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Start") }
                Spacer(Modifier.width(7.dp))
                FilledTonalButton(onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2A1619), contentColor = Rose),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Stop") }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                listOf(15, 30, 60).forEachIndexed { i, m ->
                    FilterChip(selected = sel == i, onClick = { sel = i; onTimer(m) },
                        label = { Text(if (m == 60) "1h" else "${m}m") },
                        modifier = Modifier.weight(1f).padding(end = if (i < 2) 8.dp else 0.dp))
                }
            }
        }
    }
}

@Composable
fun ScreenshotButton() {
    var loading by remember { mutableStateOf(false) }
    var b64 by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            loading = true
            scope.launch {
                Repo.send("screenshot")
                var tries = 0
                while (tries < 8) {
                    delay(1500); tries++
                    val s = Repo.getScreenshot()
                    if (s != null) { b64 = s; show = true; break }
                }
                loading = false
            }
        },
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Capture screen")
    }
    if (show && b64 != null) ScreenshotViewer(b64!!) { show = false }
}

@Composable
fun ScreenshotViewer(b64: String, onClose: () -> Unit) {
    val bmp = remember(b64) {
        try { val bytes = Base64.decode(b64, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        catch (e: Exception) { null }
    }
    Dialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)).padding(8.dp)) {
            if (bmp != null) {
                var scale by remember { mutableStateOf(1f) }
                var ox by remember { mutableStateOf(0f) }
                var oy by remember { mutableStateOf(0f) }
                Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = "screenshot",
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                ox += pan.x; oy += pan.y
                            }
                        }
                )
            } else {
                Text("Could not load image", color = Color.White, modifier = Modifier.padding(24.dp))
            }
            FilledTonalButton(onClick = onClose, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) { Text("Close") }
        }
    }
}

// -------- Hours --------
@Composable
fun HoursScreen(status: JSONObject?) {
    val h = status?.optJSONObject("hours")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("DeskTime hours", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))
                .padding(18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text("TIME WORKED", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(hms(h?.optLong("desktimeTime") ?: 0), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Effectiveness", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(h?.optDouble("efficiency") ?: 0.0).toInt()}%", fontSize = 20.sp, color = GreenText, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionList("Projects", h?.optJSONArray("projects"), project = true)
        Spacer(Modifier.height(14.dp))
        SectionList("Top apps", h?.optJSONArray("apps"), project = false)
    }
}

@Composable
fun SectionList(title: String, arr: org.json.JSONArray?, project: Boolean) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (arr == null || arr.length() == 0) {
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                for (i in 0 until minOf(arr.length(), 8)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = if (project) (o.optString("task_title").ifEmpty { o.optString("project_title") }) else o.optString("name")
                    val dur = o.optLong("duration")
                    val col = when (o.optString("productivity")) { "productive" -> Green; "unproductive" -> Rose; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (project) MaterialTheme.colorScheme.primary else col)
                        Spacer(Modifier.width(10.dp))
                        Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(hms(dur), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// -------- Settings --------
@Composable
fun SettingsScreen(dark: Boolean, onTheme: (Boolean) -> Unit, onEditConnection: () -> Unit) {
    var devices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var deviceId by remember { mutableStateOf(Repo.deviceId()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val d = Repo.listDevices(); devices = d
        if (deviceId.isEmpty() && d.isNotEmpty()) { deviceId = d[0].first; Repo.prefs.edit().putString("deviceId", deviceId).apply() }
    }
    val deviceName = devices.firstOrNull { it.first == deviceId }?.second ?: deviceId.ifEmpty { "—" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Appearance", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row {
                    SegButton("Dark", dark, Modifier.weight(1f)) { onTheme(true) }
                    Spacer(Modifier.width(8.dp))
                    SegButton("Light", !dark, Modifier.weight(1f)) { onTheme(false) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Device", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(deviceName, modifier = Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        devices.forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                deviceId = id; Repo.prefs.edit().putString("deviceId", id).apply(); expanded = false
                            })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Connection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    Repo.prefs.edit().remove("url").remove("secret").apply(); onEditConnection()
                }, modifier = Modifier.fillMaxWidth()) { Text("Edit connection") }
            }
        }
    }
}

@Composable
fun SegButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(44.dp)) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(44.dp)) { Text(text) }
    }
}

// -------- small bits --------
@Composable
fun Dot(color: Color) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

@Composable
fun StatusChip(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color); Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}
