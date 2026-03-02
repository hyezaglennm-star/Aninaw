package com.aninaw

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.systems.BlockEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlocksAdapter(
    private val onToggleDone: (BlockEntity, Boolean) -> Unit,
    private val onDelete: (BlockEntity) -> Unit
) : ListAdapter<BlockEntity, BlocksAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<BlockEntity>() {
        override fun areItemsTheSame(oldItem: BlockEntity, newItem: BlockEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BlockEntity, newItem: BlockEntity) = oldItem == newItem
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvType: TextView = v.findViewById(R.id.tvType)
        val cbDone: CheckBox = v.findViewById(R.id.cbDone)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val btnDelete: ImageView = v.findViewById(R.id.btnDelete)
        val btnDrag: ImageView = v.findViewById(R.id.btnDrag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_block_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.tvType.text = item.type
        holder.tvTitle.text = item.title

        holder.cbDone.setOnCheckedChangeListener(null)
        holder.cbDone.isChecked = item.isChecked
        holder.cbDone.visibility = if (item.type == "NOTE" || item.type == "REMINDER") View.GONE else View.VISIBLE

        holder.tvMeta.text = buildMeta(item)
        holder.tvMeta.visibility = if (holder.tvMeta.text.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.cbDone.setOnCheckedChangeListener { _, checked ->
            onToggleDone(item, checked)
        }

        holder.btnDelete.setOnClickListener { onDelete(item) }
        // btnDrag is used by ItemTouchHelper (handled in Activity), no click needed.
    }

    private fun buildMeta(item: BlockEntity): String {
        val parts = mutableListOf<String>()

        // ACTION optional timeMinutes (e.g., "5 min")
        item.timeMinutes?.let { mins ->
            if (mins > 0) parts += "$mins min"
        }

        // REMINDER stored datetime (epoch millis)
        item.remindAt?.let { ms ->
            val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            parts += fmt.format(Date(ms))
        }

        // NOTE note preview
        if (item.type == "NOTE") {
            val n = item.note?.trim().orEmpty()
            if (n.isNotBlank()) parts += n.take(42)
        } else {
            val n = item.note?.trim().orEmpty()
            if (n.isNotBlank()) parts += n.take(42)
        }

        return parts.joinToString(" • ")
    }
}