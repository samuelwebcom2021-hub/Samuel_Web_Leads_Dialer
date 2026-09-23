package com.tuempresa.autodialer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.AgendaWithContact
import com.tuempresa.autodialer.data.AgendaItemType
import java.text.SimpleDateFormat
import java.util.*

class AgendaAdapter(
    private val onClick: (AgendaWithContact) -> Unit,
    private val onDelete: (AgendaWithContact) -> Unit
) : ListAdapter<AgendaWithContact, AgendaAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val titleText: android.widget.TextView = view.findViewById(R.id.reminderTitle)
        val timeText: android.widget.TextView = view.findViewById(R.id.reminderDate)
        val reasonText: android.widget.TextView = view.findViewById(R.id.reminderReason)
        val deleteBtn: com.google.android.material.button.MaterialButton = view.findViewById(R.id.deleteReminderBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val contact = item.contact
        val agenda = item.agendaItem

        holder.titleText.text = contact?.businessName ?: "Contacto #${agenda.contactId}"
        
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        holder.timeText.text = fmt.format(Date(agenda.scheduledAt))
        
        holder.reasonText.text = agenda.reason ?: (if (agenda.type == AgendaItemType.FOLDER_RETRY) "Reintento de Carpeta" else "Recordatorio Personal")
        
        holder.itemView.setOnClickListener { onClick(item) }
        holder.deleteBtn.setOnClickListener { onDelete(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AgendaWithContact>() {
            override fun areItemsTheSame(oldItem: AgendaWithContact, newItem: AgendaWithContact) = 
                oldItem.agendaItem.id == newItem.agendaItem.id
            override fun areContentsTheSame(oldItem: AgendaWithContact, newItem: AgendaWithContact) = 
                oldItem == newItem
        }
    }
}
