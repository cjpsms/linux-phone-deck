package com.cj.phonemirror

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Home screen: a Steam-Deck-style grid of tiles that launch apps or open
 * web links on the configured PC. On open, makes sure the PC is reachable
 * (auto-discovering it if needed) before letting you tap anything.
 */
class LauncherActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var adapter: LauncherAdapter
    private lateinit var textConnStatus: TextView
    private lateinit var textEmpty: TextView

    private val addTileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val json = result.data?.getStringExtra(AddTileActivity.EXTRA_RESULT_ITEM) ?: return@registerForActivityResult
        val item = LauncherItem.fromJson(JSONObject(json)) ?: return@registerForActivityResult
        val items = Prefs.getLauncherItems(this).toMutableList()
        items.add(item)
        Prefs.saveLauncherItems(this, items)
        renderItems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_PhoneMirror_Deck)
        setContentView(R.layout.activity_launcher)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        textConnStatus = findViewById(R.id.textConnStatus)
        textEmpty = findViewById(R.id.textEmpty)

        adapter = LauncherAdapter(
            onTap = { launchTile(it) },
            onLongPress = { item, position -> confirmRemove(item, position) }
        )
        findViewById<RecyclerView>(R.id.recyclerTiles).apply {
            val cols = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
            layoutManager = GridLayoutManager(this@LauncherActivity, cols)
            adapter = this@LauncherActivity.adapter
        }

        findViewById<ImageButton>(R.id.btnAdd).setOnClickListener {
            addTileLauncher.launch(Intent(this, AddTileActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderItems()
        ensureConnected()
    }

    private fun renderItems() {
        val items = Prefs.getLauncherItems(this)
        adapter.submit(items)
        textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Confirms the saved URL/token point at a live server, auto-discovering if not. */
    private fun ensureConnected() {
        val url = Prefs.getUrl(this)
        val token = Prefs.getToken(this)

        if (url.isBlank() || token.isBlank()) {
            textConnStatus.text = "Not set up"
            showSetupDialog("Phone Deck isn't configured yet — set the PC URL and token in Settings.")
            return
        }

        textConnStatus.text = "Checking…"
        scope.launch {
            val reachable = withContext(Dispatchers.IO) { DeckClient.isReachable(url) }
            if (reachable) {
                textConnStatus.text = "Connected"
                return@launch
            }

            textConnStatus.text = "Searching…"
            val found = withContext(Dispatchers.IO) { PcDiscovery.find() }
            if (found != null) {
                Prefs.save(this@LauncherActivity, found, token)
                textConnStatus.text = "Connected"
                Toast.makeText(this@LauncherActivity, "Found PC at $found", Toast.LENGTH_SHORT).show()
            } else {
                textConnStatus.text = "PC not found"
                showSetupDialog(
                    "Couldn't reach the PC server at $url and couldn't find one on this " +
                        "network either. Make sure server.py is running and your phone is on " +
                        "the same Wi-Fi, then check Settings."
                )
            }
        }
    }

    private fun showSetupDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Can't reach PC")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ -> startActivity(Intent(this, MainActivity::class.java)) }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun launchTile(item: LauncherItem) {
        val url = Prefs.getUrl(this)
        val token = Prefs.getToken(this)
        Toast.makeText(this, "Opening ${item.label}…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (item.type) {
                    LauncherItem.Type.APP -> DeckClient.launchApp(url, token, item.target)
                    LauncherItem.Type.URL -> DeckClient.openUrl(url, token, item.target)
                }
            }
            if (!ok) Toast.makeText(this@LauncherActivity, "Failed to open ${item.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmRemove(item: LauncherItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove tile")
            .setMessage("Remove \"${item.label}\" from the launcher?")
            .setPositiveButton("Remove") { _, _ ->
                val items = Prefs.getLauncherItems(this).toMutableList()
                if (position in items.indices) {
                    items.removeAt(position)
                    Prefs.saveLauncherItems(this, items)
                    renderItems()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
