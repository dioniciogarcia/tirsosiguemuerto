package com.workspacehelper.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
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
import androidx.compose.ui.draw.scale
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

// -------- language --------
object Lang {
    var code by mutableStateOf("en")
    fun load() { code = Repo.prefs.getString("lang", "en") ?: "en" }
    fun set(c: String) { code = c; Repo.prefs.edit().putString("lang", c).apply() }
    fun t(en: String, es: String) = if (code == "es") es else en
}

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

    suspend fun getHistory(date: String): JSONObject? = withContext(Dispatchers.IO) {
        val r = Net.get(fb("devices/${deviceId()}/history/$date")) ?: return@withContext null
        try { if (r == "null") null else JSONObject(r) } catch (e: Exception) { null }
    }

    suspend fun getRoutines(): JSONObject? = withContext(Dispatchers.IO) {
        val r = Net.get(fb("devices/${deviceId()}/routines")) ?: return@withContext null
        try { if (r == "null") null else JSONObject(r) } catch (e: Exception) { null }
    }
    suspend fun addRoutine(body: String) = withContext(Dispatchers.IO) {
        Net.post(fb("devices/${deviceId()}/routines"), body)
    }
    suspend fun deleteRoutine(key: String) = withContext(Dispatchers.IO) {
        Net.req("DELETE", fb("devices/${deviceId()}/routines/$key"), null)
    }
    suspend fun setRoutineEnabled(key: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        Net.req("PUT", fb("devices/${deviceId()}/routines/$key/enabled"), if (enabled) "true" else "false")
    }
}

fun dateStr(daysAgo: Int): String {
    val c = java.util.Calendar.getInstance()
    c.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(c.time)
}

fun hms(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

fun showBiometric(activity: FragmentActivity, subtitle: String, onOk: () -> Unit) {
    val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
        onOk(); return
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("WorkspaceHelper").setSubtitle(subtitle)
        .setAllowedAuthenticators(allowed).build()
    prompt.authenticate(info)
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.prefs = getSharedPreferences("wh", Context.MODE_PRIVATE)
        Lang.load()
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
            toast(Lang.t("Sent", "Enviado"))
            val key = Repo.send(type, args)
            if (key != null) {
                repeat(6) {
                    delay(1500)
                    if (Repo.getAck()?.optString("key") == key) { toast(Lang.t("✓ Done", "✓ Listo")); return@launch }
                }
            }
        }
    }
    val cmdAuth: (String, String, String) -> Unit = { type, args, subtitle ->
        val act = ctx as? FragmentActivity
        if (Repo.prefs.getBoolean("fp", true) && act != null) showBiometric(act, subtitle) { cmd(type, args) }
        else cmd(type, args)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text(Lang.t("Home", "Inicio")) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.BarChart, null) }, label = { Text(Lang.t("Hours", "Horas")) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text(Lang.t("Settings", "Ajustes")) })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(status, cmd, cmdAuth)
                1 -> HoursScreen(status, cmd)
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
        Text(Lang.t("Enter your Firebase connection to link to your PC.",
            "Ingresa tu conexión de Firebase para enlazar tu PC."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(url, { url = it }, label = { Text(Lang.t("Firebase URL", "URL de Firebase")) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(secret, { secret = it }, label = { Text(Lang.t("Firebase secret", "Secreto de Firebase")) },
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
        ) { Text(Lang.t("Connect", "Conectar")) }
    }
}

// -------- Home --------
@Composable
fun HomeScreen(status: JSONObject?, cmd: (String, String) -> Unit, cmdAuth: (String, String, String) -> Unit) {
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
            StatusChip(if (online) Lang.t("PC online", "PC en línea") else Lang.t("PC offline", "PC desconectada"), if (online) Green else Rose)
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
                Text(if (running) Lang.t("Running", "Activo") else Lang.t("Stopped", "Detenido"), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(hms(total), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(Lang.t("worked today", "trabajado hoy"), fontSize = 10.sp, color = muted)
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(Lang.t("Productivity", "Productividad"), fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
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
        Text(Lang.t("CONTROLS", "CONTROLES"), fontSize = 11.sp, letterSpacing = 1.5.sp, color = muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        ControlCard(
            title = Lang.t("DeskTime process", "Proceso DeskTime"), subtitle = Lang.t("Open or close tracking", "Abrir o cerrar seguimiento"),
            icon = Icons.Filled.PowerSettingsNew, iconBg = Color(0xFF201C33), iconTint = Violet2,
            onStart = { cmdAuth("desktime_start", "{}", Lang.t("Confirm opening DeskTime", "Confirma abrir DeskTime")) },
            onStop = { cmdAuth("desktime_stop", "{}", Lang.t("Confirm closing DeskTime", "Confirma cerrar DeskTime")) },
            onTimer = { m -> cmd("desktime_timer", """{"minutes":$m,"direction":"open_then_close"}""") }
        )
        Spacer(Modifier.height(9.dp))
        ControlCard(
            title = Lang.t("Mouse helper · F9", "Asistente de ratón · F9"), subtitle = Lang.t("Keeps the PC active", "Mantiene la PC activa"),
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
        ) { Text(Lang.t("Panic · stop everything", "Pánico · detener todo"), fontWeight = FontWeight.SemiBold) }

        if (ts > 0) {
            val age = (System.currentTimeMillis() / 1000 - ts).coerceAtLeast(0)
            val ageTxt = if (age < 60) "${age}s" else "${age / 60}m"
            Spacer(Modifier.height(10.dp))
            Text(
                Lang.t("Updated $ageTxt ago", "Actualizado hace $ageTxt"),
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
                ) { Text(Lang.t("Start", "Iniciar")) }
                Spacer(Modifier.width(7.dp))
                FilledTonalButton(onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2A1619), contentColor = Rose),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text(Lang.t("Stop", "Detener")) }
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
        else Text(Lang.t("Capture screen", "Capturar pantalla"))
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
                Text(Lang.t("Could not load image", "No se pudo cargar la imagen"), color = Color.White, modifier = Modifier.padding(24.dp))
            }
            FilledTonalButton(onClick = onClose, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) { Text(Lang.t("Close", "Cerrar")) }
        }
    }
}

// -------- Hours --------
@Composable
fun HoursScreen(status: JSONObject?, cmd: (String, String) -> Unit) {
    val today = remember { dateStr(0) }
    var selDate by remember { mutableStateOf(today) }
    var histHours by remember { mutableStateOf<JSONObject?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val h = if (selDate == today) status?.optJSONObject("hours") else histHours
    val dates = remember { (0..6).map { dateStr(it) } }
    fun label(d: String) = when (d) { today -> Lang.t("Today", "Hoy"); dateStr(1) -> Lang.t("Yesterday", "Ayer"); else -> d }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Lang.t("DeskTime hours", "Horas de DeskTime"), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                FilledTonalButton(onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(label(selDate), fontSize = 13.sp); Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded, { expanded = false }) {
                    dates.forEach { d ->
                        DropdownMenuItem(text = { Text(label(d)) }, onClick = {
                            expanded = false; selDate = d
                            if (d == today) histHours = null
                            else {
                                histHours = null
                                cmd("fetch_history", """{"date":"$d"}""")
                                scope.launch {
                                    repeat(8) { delay(1500); val x = Repo.getHistory(d); if (x != null) { histHours = x; return@launch } }
                                }
                            }
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))
                .padding(18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(Lang.t("TIME WORKED", "TIEMPO TRABAJADO"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(hms(h?.optLong("desktimeTime") ?: 0), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Lang.t("Effectiveness", "Efectividad"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(h?.optDouble("efficiency") ?: 0.0).toInt()}%", fontSize = 20.sp, color = GreenText, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionList(Lang.t("Projects", "Proyectos"), h?.optJSONArray("projects"), project = true, date = selDate)
        Spacer(Modifier.height(14.dp))
        SectionList(Lang.t("Top apps", "Apps principales"), h?.optJSONArray("apps"), project = false, date = selDate)
    }
}

@Composable
fun SectionList(title: String, arr: org.json.JSONArray?, project: Boolean, date: String) {
    val ctx = LocalContext.current
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
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (project) MaterialTheme.colorScheme.primary else col)
                        Spacer(Modifier.width(10.dp))
                        Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(hms(dur), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (project) {
                            IconButton(onClick = {
                                val text = "$name - [start] - [end]\n(${hms(dur)} on $date)\n* Ticket #"
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                send.type = "text/plain"; send.putExtra(android.content.Intent.EXTRA_TEXT, text)
                                ctx.startActivity(android.content.Intent.createChooser(send, Lang.t("Share", "Compartir")))
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Share, Lang.t("Share", "Compartir"), modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
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
    val ctx = LocalContext.current
    var fp by remember { mutableStateOf(Repo.prefs.getBoolean("fp", true)) }
    var notif by remember { mutableStateOf(Repo.prefs.getBoolean("notif_enabled", false)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(Lang.t("Settings", "Ajustes"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(Lang.t("Language", "Idioma"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row {
                    SegButton("English", Lang.code == "en", Modifier.weight(1f)) { Lang.set("en") }
                    Spacer(Modifier.width(8.dp))
                    SegButton("Español", Lang.code == "es", Modifier.weight(1f)) { Lang.set("es") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(Lang.t("Appearance", "Apariencia"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row {
                    SegButton(Lang.t("Dark", "Oscuro"), dark, Modifier.weight(1f)) { onTheme(true) }
                    Spacer(Modifier.width(8.dp))
                    SegButton(Lang.t("Light", "Claro"), !dark, Modifier.weight(1f)) { onTheme(false) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(Lang.t("Device", "Dispositivo"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

        ToggleCard(Lang.t("Fingerprint for DeskTime", "Huella para DeskTime"), Lang.t("Confirm before start/stop", "Confirmar antes de iniciar/detener"), fp) {
            fp = it; Repo.prefs.edit().putBoolean("fp", it).apply()
        }
        Spacer(Modifier.height(12.dp))
        ToggleCard(Lang.t("PC offline alerts", "Alertas de PC desconectada"), Lang.t("Notify when the PC goes offline", "Avisar cuando la PC se desconecte"), notif) {
            notif = it; Repo.prefs.edit().putBoolean("notif_enabled", it).apply()
            try {
                if (it) {
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    val w = PeriodicWorkRequest.Builder(StatusWorker::class.java, 15, TimeUnit.MINUTES).build()
                    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                        "status-check", ExistingPeriodicWorkPolicy.UPDATE, w)
                } else {
                    WorkManager.getInstance(ctx).cancelUniqueWork("status-check")
                }
            } catch (e: Exception) { /* never crash the toggle */ }
        }
        Spacer(Modifier.height(12.dp))

        RoutinesCard()
        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(Lang.t("Connection", "Conexión"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    Repo.prefs.edit().remove("url").remove("secret").apply(); onEditConnection()
                }, modifier = Modifier.fillMaxWidth()) { Text(Lang.t("Edit connection", "Editar conexión")) }
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

@Composable
fun RoutinesCard() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var routines by remember { mutableStateOf<JSONObject?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("18:30") }
    var action by remember { mutableStateOf("desktime_close") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var actionExpanded by remember { mutableStateOf(false) }
    val actions = listOf(
        "desktime_open" to Lang.t("Open DeskTime", "Abrir DeskTime"),
        "desktime_close" to Lang.t("Close DeskTime", "Cerrar DeskTime"),
        "mouse_start" to Lang.t("Mouse helper 1h", "Asistente ratón 1h"),
        "shutdown" to Lang.t("Shut down PC", "Apagar PC"))
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    fun actionLabel(a: String) = actions.firstOrNull { it.first == a }?.second ?: a
    fun reload() { scope.launch { routines = Repo.getRoutines() } }
    LaunchedEffect(Unit) { reload() }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Lang.t("Scheduled routines", "Rutinas programadas"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { showAdd = !showAdd }) {
                    Icon(if (showAdd) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = Lang.t("Add routine", "Agregar rutina"))
                }
            }
            val r = routines
            if (r == null || r.length() == 0) {
                Text(Lang.t("No routines yet. The PC runs these automatically.",
                    "Sin rutinas aún. La PC las ejecuta automáticamente."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                r.keys().asSequence().toList().forEach { k ->
                    val o = r.optJSONObject(k) ?: return@forEach
                    val enabled = o.optBoolean("enabled", true)
                    val daysArr = o.optJSONArray("days")
                    val daysTxt = if (daysArr != null && daysArr.length() > 0)
                        " · " + (0 until daysArr.length()).joinToString("") {
                            dayLetters.getOrElse(daysArr.optInt(it)) { "?" } } else " · " + Lang.t("daily", "diario")
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(o.optString("time") + "  " + actionLabel(o.optString("type")) + daysTxt,
                            fontSize = 12.5.sp, modifier = Modifier.weight(1f),
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = enabled,
                            onCheckedChange = { v -> scope.launch { Repo.setRoutineEnabled(k, v); reload() } },
                            modifier = Modifier.scale(0.8f))
                        IconButton(onClick = { scope.launch { Repo.deleteRoutine(k); reload() } },
                            modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Delete, Lang.t("Delete", "Eliminar"),
                                modifier = Modifier.size(18.dp), tint = Rose)
                        }
                    }
                }
            }
            if (showAdd) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val p = time.split(":")
                        val hh = p.getOrNull(0)?.toIntOrNull() ?: 18
                        val mm = p.getOrNull(1)?.toIntOrNull() ?: 30
                        android.app.TimePickerDialog(ctx, { _, h, m ->
                            time = "%02d:%02d".format(h, m) }, hh, mm, true).show()
                    }) {
                        Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text(time)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { actionExpanded = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(actionLabel(action), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(actionExpanded, { actionExpanded = false }) {
                            actions.forEach { (v, lbl) ->
                                DropdownMenuItem(text = { Text(lbl) },
                                    onClick = { action = v; actionExpanded = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    dayLetters.forEachIndexed { i, d ->
                        FilterChip(selected = days.contains(i),
                            onClick = { days = if (days.contains(i)) days - i else days + i },
                            label = { Text(d, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).padding(end = if (i < 6) 3.dp else 0.dp))
                    }
                }
                Text(if (days.isEmpty()) Lang.t("No days selected = every day", "Sin días = todos los días")
                    else Lang.t("Repeats on selected days", "Se repite los días elegidos"),
                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val daysJson = if (days.isNotEmpty())
                        ""","days":[${days.sorted().joinToString(",")}]""" else ""
                    val minutes = if (action == "mouse_start") ""","minutes":60""" else ""
                    val body = """{"type":"$action","time":"$time","enabled":true$daysJson$minutes}"""
                    scope.launch { Repo.addRoutine(body); days = emptySet(); showAdd = false; reload() }
                }, modifier = Modifier.fillMaxWidth()) { Text(Lang.t("Add routine", "Agregar rutina")) }
            }
        }
    }
}

@Composable
fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
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
