package com.workspacehelper.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    private const val BASE =
        "https://github.com/dioniciogarcia/tirsosiguemuerto/releases/download/native-preview/"
    private const val APK = BASE + "WorkspaceHelper-native.apk"
    private const val VER = BASE + "native-version.txt"

    // Open with manual redirect following (GitHub release assets redirect to a CDN host).
    private fun open(urlStr: String): HttpURLConnection {
        var url = URL(urlStr)
        var conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 30000
        var hops = 0
        while (hops < 5) {
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                url = URL(url, loc)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                hops++
            } else break
        }
        return conn
    }

    /** Returns Pair(versionCode, versionName) from the release, or null on failure. */
    suspend fun latest(): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            val conn = open(VER)
            val txt = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            val lines = txt.lines()
            val vc = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: return@withContext null
            val vn = lines.getOrNull(1)?.trim() ?: ""
            vc to vn
        } catch (e: Exception) { null }
    }

    /** Downloads the APK to external files dir; returns the file or null. */
    suspend fun download(ctx: Context): File? = withContext(Dispatchers.IO) {
        try {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val out = File(dir, "WorkspaceHelper-native.apk")
            val conn = open(APK)
            if (conn.responseCode !in 200..299) { conn.disconnect(); return@withContext null }
            conn.inputStream.use { inp -> out.outputStream.use { o -> inp.copyTo(o) } }
            conn.disconnect()
            if (out.length() < 100_000) return@withContext null  // too small to be a real APK
            out
        } catch (e: Exception) { null }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            ctx, BuildConfig.APPLICATION_ID + ".fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
