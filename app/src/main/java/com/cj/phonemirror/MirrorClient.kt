package com.cj.phonemirror

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "MirrorClient"

/**
 * Posts a mirrored notification to {baseUrl}/mirror using plain HttpURLConnection
 * (no third-party HTTP dependency). Runs on whatever thread it's called from —
 * callers are expected to invoke it off the main thread.
 *
 * Returns true on a 2xx response, false otherwise. Never throws — failures are logged.
 */
object MirrorClient {

    fun postMirror(baseUrl: String, token: String, app: String, title: String, body: String): Boolean {
        if (baseUrl.isBlank()) {
            Log.w(TAG, "No PC URL configured, skipping send")
            return false
        }
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/mirror")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Token", token)
            }

            val payload = JSONObject().apply {
                put("app", app)
                put("title", title)
                put("body", body)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val ok = code in 200..299
            if (!ok) {
                Log.w(TAG, "POST $url -> HTTP $code")
            }
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Failed to POST mirror: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}
