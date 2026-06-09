package com.cj.phonemirror

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var editUrl: EditText
    private lateinit var editToken: EditText
    private lateinit var textStatus: TextView

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        editUrl = findViewById(R.id.editUrl)
        editToken = findViewById(R.id.editToken)
        textStatus = findViewById(R.id.textStatus)

        editUrl.setText(Prefs.getUrl(this))
        editToken.setText(Prefs.getToken(this))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.save(this, editUrl.text.toString(), editToken.text.toString())
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnNotificationAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnBatteryOptimization).setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }

        findViewById<Button>(R.id.btnSendTest).setOnClickListener {
            sendTest()
        }

        findViewById<Button>(R.id.btnFindPc).setOnClickListener {
            findPc()
        }
    }

    private fun findPc() {
        textStatus.text = "Status: searching for PC on the network..."
        scope.launch {
            val found = withContext(Dispatchers.IO) { PcDiscovery.find() }
            if (found != null) {
                editUrl.setText(found)
                Prefs.save(this@MainActivity, found, editToken.text.toString())
                textStatus.text = "Status: found PC at $found (saved)"
            } else {
                textStatus.text = "Status: no PC found — make sure server.py is running " +
                    "and the phone is on the same Wi-Fi/LAN"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already ignoring battery optimizations", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun sendTest() {
        // Persist whatever is currently in the fields before testing.
        Prefs.save(this, editUrl.text.toString(), editToken.text.toString())
        val url = Prefs.getUrl(this)
        val token = Prefs.getToken(this)

        textStatus.text = "Status: sending..."
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                MirrorClient.postMirror(url, token, "Phone Mirror", "Test notification", "Hello from your phone 👋")
            }
            textStatus.text = if (ok) "Status: test sent OK" else "Status: test FAILED (check URL/token, see logcat)"
        }
    }
}
