package com.cj.phonemirror

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "MirrorService"

class MirrorService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            // Never let a malformed notification crash the listener.
            Log.w(TAG, "Failed to handle notification: ${e.message}")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // don't mirror our own notifications

        val notification = sbn.notification ?: return

        // Skip ongoing / foreground-service notifications (downloads, music players, etc).
        val isOngoing = sbn.isOngoing ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && body.isEmpty()) return // skip empties

        val appLabel = resolveAppLabel(sbn.packageName)

        val url = Prefs.getUrl(applicationContext)
        val token = Prefs.getToken(applicationContext)
        if (url.isBlank()) return

        scope.launch {
            MirrorClient.postMirror(url, token, appLabel, title, body)
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val pm: PackageManager = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
