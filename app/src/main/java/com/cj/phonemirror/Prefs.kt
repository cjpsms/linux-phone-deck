package com.cj.phonemirror

import android.content.Context

object Prefs {
    private const val FILE = "phone_mirror_prefs"
    private const val KEY_URL = "pc_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_LAUNCHER_ITEMS = "launcher_items"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getUrl(context: Context): String = prefs(context).getString(KEY_URL, "") ?: ""

    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun save(context: Context, url: String, token: String) {
        prefs(context).edit()
            .putString(KEY_URL, url.trim().trimEnd('/'))
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun getLauncherItems(context: Context): List<LauncherItem> {
        val saved = prefs(context).getString(KEY_LAUNCHER_ITEMS, "") ?: ""
        if (saved.isNotBlank()) return LauncherItem.listFromJson(saved)
        return listOf(
            LauncherItem(LauncherItem.Type.APP, "org.vinegarhq.Sober", "Sober"),
            LauncherItem(LauncherItem.Type.APP, "prismlauncher", "Prism"),
            LauncherItem(LauncherItem.Type.APP, "steam", "Steam"),
            LauncherItem(LauncherItem.Type.APP, "com.obsproject.Studio", "OBS"),
        )
    }

    fun saveLauncherItems(context: Context, items: List<LauncherItem>) {
        prefs(context).edit()
            .putString(KEY_LAUNCHER_ITEMS, LauncherItem.listToJson(items))
            .apply()
    }
}
