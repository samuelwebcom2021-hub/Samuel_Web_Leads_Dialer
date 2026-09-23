package com.tuempresa.autodialer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.BatchSummary

class BatchAdapter(
    private val onOpen: (BatchSummary) -> Unit,
    private val onDelete: (BatchSummary) -> Unit
) : ListAdapter<BatchSummary, BatchAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.TextView = view.findViewById(R.id.batchName)
        val counts: android.widget.TextView = view.findViewById(R.id.batchCounts)
        val deleteButton: android.widget.ImageButton = view.findViewById(R.id.deleteBatchButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_batch, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val batch = getItem(position)
        holder.name.text = batch.importBatchName.ifBlank { "Excel sin nombre" }
        holder.counts.text = "${batch.total} contactos · ${batch.pending} pendientes · ${batch.interested} interesados"
        holder.itemView.setOnClickListener { onOpen(batch) }
        holder.deleteButton.setOnClickListener { onDelete(batch) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BatchSummary>() {
            override fun areItemsTheSame(oldItem: BatchSummary, newItem: BatchSummary) =
                oldItem.importBatchId == newItem.importBatchId
            override fun areContentsTheSame(oldItem: BatchSummary, newItem: BatchSummary) = oldItem == newItem
        }
    }
}
