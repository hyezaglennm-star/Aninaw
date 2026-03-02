package com.aninaw

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.systems.SystemEntity
import com.google.android.material.card.MaterialCardView

class SystemsAdapter(
    private val onOpen: (SystemEntity) -> Unit,
    private val onDelete: (SystemEntity) -> Unit
) : ListAdapter<SystemEntity, SystemsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<SystemEntity>() {
        override fun areItemsTheSame(oldItem: SystemEntity, newItem: SystemEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SystemEntity, newItem: SystemEntity) = oldItem == newItem
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v.findViewById(R.id.cardSystem)
        val icon: TextView = v.findViewById(R.id.tvSystemIcon)
        val name: TextView = v.findViewById(R.id.tvSystemName)
        val schedule: TextView = v.findViewById(R.id.tvSystemSchedule)
        val btnDelete: View = v.findViewById(R.id.btnDeleteSystem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_system_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.icon.text = item.icon
        holder.name.text = item.name
        holder.schedule.text = when (item.scheduleType) {
            "DAILY" -> "Daily"
            "SPECIFIC_DAYS" -> "Specific days"
            else -> item.scheduleType
        }

        holder.card.setOnClickListener { onOpen(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
}