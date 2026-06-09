package com.cj.phonemirror

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LauncherAdapter(
    private val onTap: (LauncherItem) -> Unit,
    private val onLongPress: (LauncherItem, position: Int) -> Unit,
) : RecyclerView.Adapter<LauncherAdapter.TileHolder>() {

    private val items = mutableListOf<LauncherItem>()

    fun submit(newItems: List<LauncherItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_launcher_tile, parent, false)
        return TileHolder(view)
    }

    override fun onBindViewHolder(holder: TileHolder, position: Int) {
        val item = items[position]
        holder.icon.text = if (item.type == LauncherItem.Type.URL) "🌐" else item.label.take(1).uppercase()
        holder.label.text = item.label
        holder.itemView.setOnClickListener { onTap(item) }
        holder.itemView.setOnLongClickListener {
            onLongPress(item, holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tileIcon)
        val label: TextView = view.findViewById(R.id.tileLabel)
    }
}
