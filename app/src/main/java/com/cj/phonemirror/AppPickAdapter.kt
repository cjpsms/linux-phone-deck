package com.cj.phonemirror

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppPickAdapter(private val onPick: (RemoteApp) -> Unit) :
    RecyclerView.Adapter<AppPickAdapter.RowHolder>() {

    private val all = mutableListOf<RemoteApp>()
    private val shown = mutableListOf<RemoteApp>()

    fun submit(apps: List<RemoteApp>) {
        all.clear(); all.addAll(apps)
        filter("")
    }

    fun filter(query: String) {
        shown.clear()
        if (query.isBlank()) {
            shown.addAll(all)
        } else {
            val q = query.trim().lowercase()
            shown.addAll(all.filter { it.name.lowercase().contains(q) })
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_pick, parent, false)
        return RowHolder(view)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val app = shown[position]
        holder.name.text = app.name
        holder.itemView.setOnClickListener { onPick(app) }
    }

    override fun getItemCount(): Int = shown.size

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.pickAppName)
    }
}
