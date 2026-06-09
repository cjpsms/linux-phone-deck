package com.cj.phonemirror

import org.json.JSONArray
import org.json.JSONObject

/**
 * One tile on the launcher grid: either a PC app discovered via GET /apps
 * (launched by [target] = its scanned id) or a custom web link
 * (launched by [target] = a full http(s) URL, opened with xdg-open on the PC).
 */
data class LauncherItem(val type: Type, val target: String, val label: String) {

    enum class Type { APP, URL }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("target", target)
        put("label", label)
    }

    companion object {
        fun fromJson(o: JSONObject): LauncherItem? {
            val type = try {
                Type.valueOf(o.optString("type"))
            } catch (e: IllegalArgumentException) {
                return null
            }
            val target = o.optString("target")
            val label = o.optString("label")
            if (target.isBlank() || label.isBlank()) return null
            return LauncherItem(type, target, label)
        }

        fun listToJson(items: List<LauncherItem>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<LauncherItem> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
