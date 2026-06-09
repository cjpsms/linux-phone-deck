package com.cj.phonemirror

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Lets the user add a launcher tile: either a custom web link, or one of the
 * apps the PC server scanned from its .desktop files. Returns the new
 * [LauncherItem] (as JSON in [EXTRA_RESULT_ITEM]) via setResult/RESULT_OK.
 */
class AddTileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT_ITEM = "result_item"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var adapter: AppPickAdapter
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_PhoneMirror_Deck)
        setContentView(R.layout.activity_add_tile)

        val editLabel = findViewById<EditText>(R.id.editLinkLabel)
        val editUrl = findViewById<EditText>(R.id.editLinkUrl)
        val editFilter = findViewById<EditText>(R.id.editFilter)
        textStatus = findViewById(R.id.textAppsStatus)
        val recycler = findViewById<RecyclerView>(R.id.recyclerApps)

        findViewById<Button>(R.id.btnAddLink).setOnClickListener {
            val label = editLabel.text.toString().trim()
            val url = editUrl.text.toString().trim()
            if (label.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                Toast.makeText(this, "Enter a label and a http(s):// URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            finishWith(LauncherItem(LauncherItem.Type.URL, url, label))
        }

        adapter = AppPickAdapter { app ->
            finishWith(LauncherItem(LauncherItem.Type.APP, app.id, app.name))
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        editFilter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadApps()
    }

    private fun loadApps() {
        val url = Prefs.getUrl(this)
        val token = Prefs.getToken(this)
        scope.launch {
            val apps = withContext(Dispatchers.IO) { DeckClient.fetchApps(url, token) }
            if (apps == null) {
                textStatus.text = "Couldn't load app list from PC (check connection in Settings)"
            } else if (apps.isEmpty()) {
                textStatus.text = "PC reported no launchable apps"
            } else {
                textStatus.text = "${apps.size} apps found — tap one to add it"
                adapter.submit(apps)
            }
        }
    }

    private fun finishWith(item: LauncherItem) {
        val data = Intent().putExtra(EXTRA_RESULT_ITEM, item.toJson().toString())
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
