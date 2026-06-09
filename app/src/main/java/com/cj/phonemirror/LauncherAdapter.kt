package com.cj.phonemirror

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LauncherAdapter(
    private val onTap: (LauncherItem) -> Unit,
    private val onLongPress: (LauncherItem, position: Int) -> Unit,
    private val onAddTap: () -> Unit,
) : RecyclerView.Adapter<LauncherAdapter.TileHolder>() {

    private val items = mutableListOf<LauncherItem>()
    private val MIN_CELLS = 12

    fun submit(newItems: List<LauncherItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun totalCells() = maxOf(items.size + 1, MIN_CELLS)

    override fun getItemViewType(position: Int) = if (position < items.size) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_launcher_tile, parent, false)
        return TileHolder(view)
    }

    override fun onBindViewHolder(holder: TileHolder, position: Int) {
        if (position < items.size) {
            val item = items[position]
            holder.icon.text = if (item.type == LauncherItem.Type.URL) "🌐" else item.label.take(1).uppercase()
            holder.icon.alpha = 1f
            holder.label.text = item.label
            holder.label.alpha = 1f
            holder.itemView.setOnClickListener { onTap(item) }
            holder.itemView.setOnLongClickListener {
                onLongPress(item, holder.bindingAdapterPosition)
                true
            }
        } else {
            holder.icon.text = "+"
            holder.icon.alpha = 0.25f
            holder.label.text = ""
            holder.label.alpha = 0f
            holder.itemView.setOnClickListener { onAddTap() }
            holder.itemView.setOnLongClickListener(null)
        }
    }

    override fun getItemCount() = totalCells()

    class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tileIcon)
        val label: TextView = view.findViewById(R.id.tileLabel)
    }
}
