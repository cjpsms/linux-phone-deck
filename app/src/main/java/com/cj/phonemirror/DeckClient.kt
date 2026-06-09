package com.cj.phonemirror

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "DeckClient"

/** One entry from the PC server's GET /apps — a launchable .desktop app. */
data class RemoteApp(val id: String, val name: String, val icon: String)

/**
 * Talks to the phone-deck PC server's launcher endpoints (GET /apps,
 * POST /launch) plus a quick reachability check. Plain HttpURLConnection,
 * same style as [MirrorClient]. All calls are blocking — run off the main thread.
 */
object DeckClient {

    /** Quick GET / with a short timeout — true if the server answers at all. */
    fun isReachable(baseUrl: String, timeoutMs: Int = 1500): Boolean {
        if (baseUrl.isBlank()) return false
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            conn.responseCode in 200..499 // any HTTP response means something is listening
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** Fetches the server's scanned .desktop app list, or null on any failure. */
    fun fetchApps(baseUrl: String, token: String): List<RemoteApp>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$baseUrl/apps").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("X-Token", token)
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "GET /apps -> HTTP ${conn.responseCode}")
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("apps")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteApp(o.optString("id"), o.optString("name"), o.optString("icon"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchApps failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Launches a previously-discovered app by its scanned id. */
    fun launchApp(baseUrl: String, token: String, appId: String): Boolean =
        postLaunch(baseUrl, token, JSONObject().put("id", appId))

    /** Opens a URL on the PC with its default browser (server runs xdg-open). */
    fun openUrl(baseUrl: String, token: String, url: String): Boolean =
        postLaunch(baseUrl, token, JSONObject().put("url", url))

    private fun postLaunch(baseUrl: String, token: String, payload: JSONObject): Boolean {
        if (baseUrl.isBlank()) return false
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$baseUrl/launch").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Token", token)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val ok = conn.responseCode in 200..299
            if (!ok) Log.w(TAG, "POST /launch -> HTTP ${conn.responseCode}")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "launch failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}
