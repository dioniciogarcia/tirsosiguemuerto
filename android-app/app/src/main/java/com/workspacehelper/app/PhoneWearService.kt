package com.workspacehelper.app

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Receives messages from the watch companion and relays them to Firebase using
 * the PHONE's stored connection and selected device. The watch never touches
 * Firebase itself. The system binds this service on demand, so it works even
 * when the phone app UI is closed.
 *
 *   watch -> "/wh/cmd"        {"type":..,"args":"{..}"}  -> run, then reply status
 *   watch -> "/wh/status_req"                            -> reply status
 *   phone -> "/wh/status"     <status json>
 */
class PhoneWearService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        if (!Repo.isReady()) Repo.prefs = getSharedPreferences("wh", MODE_PRIVATE)
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/wh/cmd" -> {
                try {
                    val j = JSONObject(String(event.data))
                    val type = j.optString("type")
                    var args = j.optString("args", "{}")
                    if (args.isEmpty()) args = "{}"
                    if (type.isNotEmpty()) runBlocking { Repo.send(type, args) }
                } catch (e: Exception) { /* ignore malformed */ }
                replyStatus(event.sourceNodeId)
            }
            "/wh/status_req" -> replyStatus(event.sourceNodeId)
        }
    }

    private fun replyStatus(nodeId: String) {
        val st = try { runBlocking { Repo.getStatus() } } catch (e: Exception) { null }
            ?: JSONObject()
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, "/wh/status", st.toString().toByteArray())
        } catch (e: Exception) { /* node gone */ }
    }
}
