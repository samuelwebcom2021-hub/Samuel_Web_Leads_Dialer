package com.tuempresa.autodialer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.ReminderEntity
import java.text.SimpleDateFormat
import java.util.*

class ReminderAdapter(
    private val onDelete: (ReminderEntity) -> Unit
) : ListAdapter<ReminderEntity, ReminderAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.reminderTitle)
        private val date: TextView = view.findViewById(R.id.reminderDate)
        private val deleteBtn: View = view.findViewById(R.id.deleteReminderBtn)

        fun bind(item: ReminderEntity) {
            title.text = item.title
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            date.text = fmt.format(Date(item.triggerTimeMillis))
            
            deleteBtn.setOnClickListener { onDelete(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ReminderEntity>() {
        override fun areItemsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity) = oldItem == newItem
    }
}
