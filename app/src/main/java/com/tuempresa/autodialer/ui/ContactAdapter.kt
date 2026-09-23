package com.tuempresa.autodialer.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.ContactStatus
import com.tuempresa.autodialer.excel.WebsiteClassifier
import org.json.JSONObject

class ContactAdapter(
    private val onClick: ((ContactEntity) -> Unit)? = null
) : ListAdapter<ContactEntity, ContactAdapter.ViewHolder>(DIFF) {

    /** Guarda el último estado visto de cada contacto (por ID, no por posición de la vista
     *  reciclada) para saber si de verdad cambió y así animar la insignia solo en ese caso. */
    private val lastKnownStatus = mutableMapOf<Long, String>()

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val businessText: android.widget.TextView = view.findViewById(R.id.businessText)
        val phoneText: android.widget.TextView = view.findViewById(R.id.phoneText)
        val websiteBadge: android.widget.TextView = view.findViewById(R.id.websiteBadge)
        val statusBadge: android.widget.TextView = view.findViewById(R.id.statusBadge)
        val whatsappBadge: android.widget.TextView = view.findViewById(R.id.whatsappBadge)
        val extraDataText: android.widget.TextView = view.findViewById(R.id.extraDataText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        holder.businessText.text = contact.businessName.ifBlank { "(sin nombre de negocio)" }
        holder.phoneText.text = contact.phoneNumber
        holder.statusBadge.text = statusLabel(contact)
        holder.itemView.setOnClickListener { onClick?.invoke(contact) }

        val previousStatus = lastKnownStatus[contact.id]
        if (previousStatus != null && previousStatus != contact.status) {
            animateStatusChange(holder.statusBadge)
        }
        lastKnownStatus[contact.id] = contact.status

        bindWebsiteBadge(holder, contact)

        if (!contact.whatsappNumber.isNullOrBlank()) {
            holder.whatsappBadge.visibility = android.view.View.VISIBLE
            holder.whatsappBadge.text = "✅ WA: ${contact.whatsappNumber}"
        } else {
            holder.whatsappBadge.visibility = android.view.View.GONE
        }

        val ratingText = if (contact.rating != null || contact.reviewCount != null) {
            "⭐ ${contact.rating ?: "?"} (${contact.reviewCount ?: 0} reseñas)"
        } else null

        holder.extraDataText.text = ratingText
    }

    /** Pequeño "pop" en la insignia cuando su estado de verdad cambió (ej: Llamando → Interesado). */
    private fun animateStatusChange(view: android.widget.TextView) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .scaleX(1.18f).scaleY(1.18f)
            .setDuration(140)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            .start()
    }

    private fun bindWebsiteBadge(holder: ViewHolder, contact: ContactEntity) {
        val type = contact.websiteType?.let { runCatching { WebsiteClassifier.Type.valueOf(it) }.getOrNull() }
        // Insignias pensadas para fondo oscuro: fondo saturado oscuro + texto claro del mismo tono.
        val (text, bg, fg) = when (type) {
            WebsiteClassifier.Type.DOMINIO_PROPIO -> Triple("🌐 Sitio propio", "#1B3A24", "#7CE18B")
            WebsiteClassifier.Type.RED_SOCIAL -> Triple("📱 Red social", "#3A2A10", "#FFB74D")
            WebsiteClassifier.Type.CONSTRUCTOR_WEB -> Triple("🛠️ Constructor web", "#1A2E35", "#80DEEA")
            WebsiteClassifier.Type.UNRECOGNIZED -> Triple("Enlace sin identificar", "#2A2E37", "#B0B6C0")
            WebsiteClassifier.Type.SIN_SITIO_WEB, null -> Triple("Sin sitio web", "#3A1518", "#EF7A75")
        }
        holder.websiteBadge.text = text
        holder.websiteBadge.setBackgroundColor(Color.parseColor(bg))
        holder.websiteBadge.setTextColor(Color.parseColor(fg))
    }

    private fun statusLabel(contact: ContactEntity): String = when (contact.status) {
        ContactStatus.PENDING.name -> "Pendiente (intento ${contact.attemptCount}/2)"
        ContactStatus.IN_PROGRESS.name -> "Llamando ahora…"
        ContactStatus.AWAITING_OUTCOME.name -> "Contestó — falta calificar"
        ContactStatus.INTERESTED.name -> "Interesado 👍"
        ContactStatus.NOT_INTERESTED.name -> "No interesado 👎"
        ContactStatus.AWAITING_RETRY_TIME.name -> "Sin respuesta – elige hora para mañana"
        ContactStatus.SCHEDULED_RETRY.name -> {
            val time = contact.nextAttemptAt?.let {
                java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
            }
            if (time != null) "Reintento programado — $time (toca para cambiar)" else "Reintento programado"
        }
        ContactStatus.CANCELLED.name -> "Cancelado"
        else -> contact.status
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ContactEntity>() {
            override fun areItemsTheSame(oldItem: ContactEntity, newItem: ContactEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ContactEntity, newItem: ContactEntity) = oldItem == newItem
        }
    }
}
