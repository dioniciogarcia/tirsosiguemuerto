package com.workspacehelper.watch

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Green = Color(0xFF34D399)
private val Rose = Color(0xFFFB7185)
private val Violet = Color(0xFF7C6CF6)
private val Muted = Color(0xFF9AA3B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("wh", MODE_PRIVATE)
        setContent { WatchScreen(prefs) }
    }
}

private fun hms(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

@Composable
fun WatchScreen(prefs: SharedPreferences) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var es by remember { mutableStateOf(prefs.getString("lang", "en") == "es") }
    fun t(en: String, esS: String) = if (es) esS else en

    var status by remember { mutableStateOf<JSONObject?>(null) }
    // null = still checking, true = phone reachable, false = no phone
    var connected by remember { mutableStateOf<Boolean?>(null) }

    val msgClient = remember { Wearable.getMessageClient(ctx) }
    val nodeClient = remember { Wearable.getNodeClient(ctx) }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    // Pending confirmation: (command type, question label). Null = none.
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }

    var cachedNode by remember { mutableStateOf<String?>(null) }
    suspend fun phoneNode(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!force && cachedNode != null) return@withContext cachedNode
        try {
            val nodes = nodeClient.connectedNodes.await()
            val id = (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
            cachedNode = id
            id
        } catch (e: Exception) { null }
    }

    suspend fun send(path: String, payload: String) {
        var id = phoneNode()
        if (id == null) { connected = false; return }
        try {
            msgClient.sendMessage(id, path, payload.toByteArray()).await()
            if (connected == null) connected = true
        } catch (e: Exception) {
            // Node may have changed; refresh once and retry before giving up.
            cachedNode = null
            id = phoneNode(force = true)
            if (id == null) { connected = false; return }
            try {
                msgClient.sendMessage(id, path, payload.toByteArray()).await()
                if (connected == null) connected = true
            } catch (e2: Exception) { connected = false }
        }
    }

    fun cmd(type: String) {
        scope.launch { send("/wh/cmd", """{"type":"$type","args":"{}"}""") }
    }

    DisposableEffect(Unit) {
        val listener = MessageClient.OnMessageReceivedListener { ev ->
            if (ev.path == "/wh/status") {
                status = try { JSONObject(String(ev.data)) } catch (e: Exception) { null }
                connected = true
            }
        }
        msgClient.addListener(listener)
        onDispose { msgClient.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            send("/wh/status_req", "ping")
            delay(4000)
        }
    }

    val ts = status?.optLong("ts") ?: 0L
    val online = status?.optBoolean("online") == true &&
        (System.currentTimeMillis() / 1000.0 - ts < 30)
    val running = status?.optBoolean("desktime_running") == true
    val hours = status?.optJSONObject("hours")
    val secs = hours?.optLong("desktimeTime") ?: 0L
    val prod = hours?.optDouble("productivity") ?: 0.0

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        val p = pending
        if (p != null) {
            ConfirmScreen(
                question = p.second, es = es,
                onYes = { cmd(p.first); pending = null },
                onNo = { pending = null }
            )
            return@Scaffold
        }

        // Keep rotary (bezel/crown) focus on the list.
        LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (e: Exception) {} }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("WorkspaceHelper", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground)
            }

            // Connection / PC status
            item {
                val (label, color) = when {
                    connected == false -> t("Phone not connected", "Teléfono no conectado") to Rose
                    status == null -> t("Connecting…", "Conectando…") to Muted
                    online -> t("PC online", "PC en línea") to Green
                    else -> t("PC offline", "PC desconectada") to Rose
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier
                        .height(8.dp).width(8.dp)
                        .padding(0.dp)) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawCircle(color)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 13.sp, color = color)
                }
            }

            // DeskTime summary
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        if (running) t("DeskTime running", "DeskTime activo")
                        else t("DeskTime stopped", "DeskTime detenido"),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = if (running) Green else Muted)
                    if (secs > 0) {
                        Text(hms(secs) + "  ·  ${prod.toInt()}%",
                            fontSize = 12.sp, color = MaterialTheme.colors.onBackground)
                    }
                }
            }

            item {
                Chip(
                    onClick = { pending = "desktime_start" to t("Open DeskTime?", "¿Abrir DeskTime?") },
                    label = { Text(t("Open DeskTime", "Abrir DeskTime")) },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Violet),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = { pending = "desktime_stop" to t("Close DeskTime?", "¿Cerrar DeskTime?") },
                    label = { Text(t("Close DeskTime", "Cerrar DeskTime")) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = { pending = "panic" to t("Stop everything?", "¿Detener todo?") },
                    label = { Text(t("Panic", "Pánico")) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color(0xFF2A1619), contentColor = Rose),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    CompactChip(
                        onClick = { es = false; prefs.edit().putString("lang", "en").apply() },
                        label = { Text("EN", fontSize = 11.sp) },
                        colors = if (!es) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors()
                    )
                    Spacer(Modifier.width(6.dp))
                    CompactChip(
                        onClick = { es = true; prefs.edit().putString("lang", "es").apply() },
                        label = { Text("ES", fontSize = 11.sp) },
                        colors = if (es) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors()
                    )
                }
            }

            item {
                Text(
                    t("Controlled through your phone", "Controlado desde tu teléfono"),
                    fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ConfirmScreen(question: String, es: Boolean, onYes: () -> Unit, onNo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            question, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, color = MaterialTheme.colors.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Chip(
            onClick = onYes,
            label = { Text(if (es) "Confirmar" else "Confirm") },
            colors = ChipDefaults.primaryChipColors(backgroundColor = Green),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Chip(
            onClick = onNo,
            label = { Text(if (es) "Cancelar" else "Cancel") },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
