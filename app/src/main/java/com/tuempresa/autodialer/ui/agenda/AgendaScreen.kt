package com.tuempresa.autodialer.ui.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.data.AgendaWithContact
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AgendaScreen(
    folderRetries: List<AgendaWithContact>,
    personalReminders: List<AgendaWithContact>,
    onItemClick: (AgendaWithContact) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        Text(
            text = "Agenda",
            style = MaterialTheme.typography.headlineLarge,
            color = GoldAccent,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Llamadas programadas y recordatorios",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AgendaSectionHeader("Reintentos de Carpetas", Icons.Default.Folder)
            }
            if (folderRetries.isEmpty()) {
                item { Text("No hay reintentos programados", color = OnSurfaceMuted, fontSize = 13.sp) }
            } else {
                items(folderRetries) { item ->
                    AgendaListItem(item = item, onClick = { onItemClick(item) })
                }
            }

            item {
                AgendaSectionHeader("Recordatorios Personales", Icons.Default.Person)
            }
            if (personalReminders.isEmpty()) {
                item { Text("No hay recordatorios manuales", color = OnSurfaceMuted, fontSize = 13.sp) }
            } else {
                items(personalReminders) { item ->
                    AgendaListItem(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}
}

@Composable
fun AgendaSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun AgendaListItem(item: AgendaWithContact, onClick: () -> Unit) {
    val fmt = SimpleDateFormat("d MMMM · HH:mm", Locale.getDefault())
    val dateStr = fmt.format(Date(item.agendaItem.scheduledAt))

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.contact?.businessName ?: "Recordatorio",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.agendaItem.reason ?: "Sin especificar",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                Text(
                    dateStr,
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = OnSurfaceMuted)
        }
    }
}
