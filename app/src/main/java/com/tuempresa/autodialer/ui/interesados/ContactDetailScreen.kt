package com.tuempresa.autodialer.ui.interesados

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuempresa.autodialer.data.CallAttemptEntity
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.data.ContactExtraFieldEntity
import com.tuempresa.autodialer.ui.ContactsViewModel
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onBackClick: () -> Unit,
    viewModel: ContactsViewModel = viewModel()
) {
    var contact by remember { mutableStateOf<ContactEntity?>(null) }
    var history by remember { mutableStateOf<List<CallAttemptEntity>>(emptyList()) }
    var extraFields by remember { mutableStateOf<List<ContactExtraFieldEntity>>(emptyList()) }
    
    LaunchedEffect(contactId) {
        contact = viewModel.getContactById(contactId)
        history = viewModel.attemptHistory(contactId)
        // Necesitamos un método para obtener extraFields en el ViewModel o usar el DB directamente
        // Por ahora simularemos o usaremos el DAO si es accesible (no lo es directamente aquí)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Contacto", color = GoldAccent) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (contact == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
        } else {
            val c = contact!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(text = c.businessName, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = c.phoneNumber, style = MaterialTheme.typography.titleLarge, color = GoldAccent)
                    Spacer(Modifier.height(8.dp))
                    StatusBadge(status = c.status)
                }

                item {
                    SectionHeader("INFORMACIÓN DE CONTACTO")
                    InfoRow(Icons.Default.Phone, "Teléfono", c.phoneNumber)
                    c.ownerPhone?.let { InfoRow(Icons.Default.Person, "Número Dueño", it) }
                    c.whatsappNumber?.let { InfoRow(Icons.Default.Message, "WhatsApp", it) }
                    c.websiteRaw?.let { InfoRow(Icons.Default.Language, "Sitio Web", it) }
                }

                if (!c.notes.isNullOrBlank()) {
                    item {
                        SectionHeader("NOTAS")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
                        ) {
                            Text(text = c.notes!!, modifier = Modifier.padding(16.dp), color = Color.White)
                        }
                    }
                }

                item {
                    SectionHeader("HISTORIAL DE LLAMADAS")
                }

                if (history.isEmpty()) {
                    item { Text("No hay intentos registrados", color = OnSurfaceMuted) }
                } else {
                    items(history.reversed()) { attempt ->
                        HistoryItem(attempt)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = GoldAccent,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "INTERESTED" -> Color(0xFF4CAF50)
        "NOT_INTERESTED" -> Color(0xFFEF5350)
        "PENDING" -> Color(0xFFFFD54F)
        else -> Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HistoryItem(attempt: CallAttemptEntity) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(attempt.timestampMillis))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = dateStr, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = attempt.resultLabel, color = GoldAccent)
            }
            if (attempt.durationMillis > 0) {
                val sec = attempt.durationMillis / 1000
                Text(text = "Duración: ${sec}s", color = OnSurfaceMuted, fontSize = 12.sp)
            }
            if (!attempt.notes.isNullOrBlank()) {
                Text(text = attempt.notes!!, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
