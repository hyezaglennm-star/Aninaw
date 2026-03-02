package com.aninaw

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ProofCardUi(
    val value: String,
    val label: String,
    val hint: String
)

class ProofCardAdapter : RecyclerView.Adapter<ProofCardAdapter.VH>() {

    private val items = mutableListOf<ProofCardUi>()

    fun submit(newItems: List<ProofCardUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bigValue: TextView = itemView.findViewById(R.id.bigValue)
        val label: TextView = itemView.findViewById(R.id.label)
        val hint: TextView = itemView.findViewById(R.id.hint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_proof_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bigValue.text = item.value
        holder.label.text = item.label
        holder.hint.text = item.hint
    }

    override fun getItemCount(): Int = items.size
}