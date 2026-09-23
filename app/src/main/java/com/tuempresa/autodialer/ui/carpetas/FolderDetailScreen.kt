package com.tuempresa.autodialer.ui.carpetas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderName: String,
    contacts: List<ContactEntity>,
    isDialing: Boolean,
    isPaused: Boolean,
    remainingWaitSeconds: Int = 0,
    onBackClick: () -> Unit,
    onStartStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSkipClick: () -> Unit,
    onContactClick: (ContactEntity) -> Unit,
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
            // Panel de Control (Estilo Image 1)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                shape = MaterialTheme.shapes.large,
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onStartStopClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDialing) Color(0xFFD32F2F) else GoldAccent,
                            contentColor = Color.Black
                        ),
                        shape = CircleShape
                    ) {
                        Icon(if (isDialing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isDialing) "DETENER" else "INICIAR", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    if (isDialing) {
                        if (remainingWaitSeconds > 0) {
                            Text(
                                text = "Próxima llamada en $remainingWaitSeconds segundos...",
                                color = GoldAccent,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onPauseClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape
                            ) {
                                Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPaused) "REANUDAR" else "PAUSAR", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onSkipClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text("SALTAR", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    onSearch(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Buscar contacto o número...", color = OnSurfaceMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = SurfaceVariantDark,
                    focusedContainerColor = SurfaceVariantDark,
                    unfocusedContainerColor = SurfaceVariantDark,
                    cursorColor = GoldAccent
                ),
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(contacts) { contact ->
                    ContactListItem(contact = contact, onClick = { onContactClick(contact) })
                }
            }
        }
    }
}
}

@Composable
fun ContactListItem(contact: ContactEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = contact.businessName.ifBlank { "Sin nombre" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                // Badge de Sitio propio (Image 1)
                val (siteText, siteBg, siteTextCol) = when (contact.websiteType) {
                    "DOMINIO_PROPIO", "CONSTRUCTOR_WEB", "OWN_SITE" -> Triple("Sitio propio", Color(0xFF1B382B), Color(0xFF81C784))
                    "RED_SOCIAL" -> Triple("Red social", Color(0xFF152A38), Color(0xFF64B5F6))
                    "MARKETPLACE" -> Triple("Marketplace", Color(0xFF152A38), Color(0xFF4FC3F7))
                    else -> Triple("Sin sitio", Color(0xFF2B2B2B), Color.Gray)
                }

                Surface(
                    color = siteBg,
                    shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, siteTextCol.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(siteTextCol)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = siteText,
                            color = siteTextCol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = contact.phoneNumber,
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Información de estado y rating (Image 1)
            val statusLabel = when (contact.status) {
                "PENDING" -> "Pendiente"
                "INTERESTED" -> "Interesado"
                "NOT_INTERESTED" -> "No interesado"
                "SCHEDULED_RETRY" -> "Programado"
                "AWAITING_OUTCOME" -> "Esperando resultado"
                "WRONG_NUMBER" -> "Número equivocado"
                else -> contact.status
            }
            
            Text(
                text = "$statusLabel (intento ${contact.attemptCount}/2)",
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (contact.rating != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${contact.rating} (${contact.reviewCount ?: 0} reseñas)",
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
