package com.tuempresa.autodialer.ui.estadisticas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuempresa.autodialer.data.BatchSummary
import com.tuempresa.autodialer.data.DailyStat
import com.tuempresa.autodialer.ui.ContactsViewModel
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark

@Composable
fun StatsScreen(viewModel: ContactsViewModel = viewModel()) {
    val batches by viewModel.batches.observeAsState(emptyList())
    val contacts by viewModel.contacts.observeAsState(emptyList())
    val dailyStats by viewModel.dailyStats.observeAsState(emptyList())
    val personalReminders by viewModel.personalReminders.observeAsState(emptyList())
    val folderRetries by viewModel.folderRetries.observeAsState(emptyList())

    // Cálculos en tiempo real verificados desde la base de datos
    val totalContacts = contacts.size.coerceAtLeast(batches.sumOf { it.total })
    val totalInterested = contacts.count { it.status == "INTERESTED" }.coerceAtLeast(batches.sumOf { it.interested })
    val totalNotInterested = contacts.count { it.status == "NOT_INTERESTED" }.coerceAtLeast(batches.sumOf { it.notInterested })
    val totalPending = contacts.count { it.status == "PENDING" || it.status == "SCHEDULED_RETRY" }.coerceAtLeast(batches.sumOf { it.pending })
    val totalCallsMade = dailyStats.sumOf { it.totalAttempts }

    val successRate = if (totalContacts > 0) {
        ((totalInterested.toFloat() / totalContacts) * 100).toInt()
    } else 0

    val totalScheduled = personalReminders.size + folderRetries.size
    val whatsappFollowUps = personalReminders.count { it.agendaItem.reason?.contains("WhatsApp", ignoreCase = true) == true }
    val callFollowUps = personalReminders.count { it.agendaItem.reason?.contains("Llamada", ignoreCase = true) == true }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // ENCABEZADO Y BADGE TIEMPO REAL
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Estadísticas",
                        style = MaterialTheme.typography.headlineLarge,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Resumen de actividad comercial",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                }

                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TIEMPO REAL",
                            color = Color(0xFF81C784),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // TARJETA DE RESUMEN GENERAL (Estilo carpetas verificado)
        item {
            GeneralSummaryCard(
                active = totalContacts,
                interested = totalInterested,
                notInterested = totalNotInterested,
                successRate = successRate
            )
        }

        // GRID DE 4 MÉTRICAS CLAVE
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Llamadas Realizadas",
                        value = totalCallsMade.toString(),
                        icon = Icons.Default.Call,
                        modifier = Modifier.weight(1f),
                        color = GoldAccent
                    )
                    StatCard(
                        label = "Contactos Interesados",
                        value = totalInterested.toString(),
                        icon = Icons.Default.ThumbUp,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF4CAF50)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Seguimientos Agenda",
                        value = totalScheduled.toString(),
                        icon = Icons.Default.Event,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF64B5F6)
                    )
                    StatCard(
                        label = "Reintentos Carpeta",
                        value = folderRetries.size.toString(),
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFFFB74D)
                    )
                }
            }
        }

        // BARRA DE CONVERSIÓN Y DISTRIBUCIÓN
        item {
            ConversionBarCard(
                total = totalContacts,
                interested = totalInterested,
                notInterested = totalNotInterested,
                pending = totalPending
            )
        }

        // DESGLOSE DE AGENDA Y SEGUIMIENTOS
        item {
            AgendaBreakdownCard(
                whatsappCount = whatsappFollowUps,
                callCount = callFollowUps,
                totalPersonal = personalReminders.size,
                totalFolderRetries = folderRetries.size
            )
        }

        // DESGLOSE POR CARPETA / CAMPAÑA
        if (batches.isNotEmpty()) {
            item {
                Text(
                    text = "RENDIMIENTO POR CARPETA",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(batches) { batch ->
                FolderStatItem(batch)
            }
        }

        // HISTÓRICO DE ACTIVIDAD DIARIA
        item {
            Text(
                text = "HISTORIAL DE ACTIVIDAD DIARIA",
                style = MaterialTheme.typography.labelSmall,
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (dailyStats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Aún no hay llamadas registradas hoy", color = OnSurfaceMuted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(dailyStats.reversed()) { stat ->
                DailyStatItem(stat)
            }
        }
    }
}

@Composable
fun GeneralSummaryCard(
    active: Int,
    interested: Int,
    notInterested: Int,
    successRate: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "RESUMEN GENERAL (TODAS TUS CARPETAS)",
                style = MaterialTheme.typography.labelSmall,
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(icon = Icons.Default.Folder, value = active.toString(), label = "Activos", modifier = Modifier.weight(1f))
                SummaryItem(icon = Icons.Default.ThumbUp, value = interested.toString(), label = "Interesados", modifier = Modifier.weight(1f))
                SummaryItem(icon = Icons.Default.Delete, value = notInterested.toString(), label = "No interesados", modifier = Modifier.weight(1f))
                SummaryItem(icon = Icons.Default.Schedule, value = "$successRate%", label = "Tasa Éxito", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SummaryItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(text = label, color = OnSurfaceMuted, fontSize = 10.sp)
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = GoldAccent
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(text = value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = label, color = OnSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ConversionBarCard(
    total: Int,
    interested: Int,
    notInterested: Int,
    pending: Int
) {
    val totalCount = total.coerceAtLeast(1).toFloat()
    val interestedRatio = (interested / totalCount).coerceIn(0f, 1f)
    val notInterestedRatio = (notInterested / totalCount).coerceIn(0f, 1f)
    val pendingRatio = (pending / totalCount).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DISTRIBUCIÓN DE PROSPECCIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Barra segmentada multicolor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariantDark.copy(alpha = 0.5f))
            ) {
                if (interestedRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(interestedRatio)
                            .fillMaxHeight()
                            .background(Color(0xFF4CAF50))
                    )
                }
                if (notInterestedRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(notInterestedRatio)
                            .fillMaxHeight()
                            .background(Color(0xFFE53935))
                    )
                }
                if (pendingRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(pendingRatio)
                            .fillMaxHeight()
                            .background(GoldAccent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Leyendas con valores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(color = Color(0xFF4CAF50), label = "Interesados", value = "$interested (${(interestedRatio * 100).toInt()}%)")
                LegendItem(color = Color(0xFFE53935), label = "No Interesados", value = "$notInterested (${(notInterestedRatio * 100).toInt()}%)")
                LegendItem(color = GoldAccent, label = "Pendientes", value = "$pending (${(pendingRatio * 100).toInt()}%)")
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, color = OnSurfaceMuted, fontSize = 10.sp)
            Text(text = value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AgendaBreakdownCard(
    whatsappCount: Int,
    callCount: Int,
    totalPersonal: Int,
    totalFolderRetries: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEGUIMIENTOS Y AGENDA",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${totalPersonal + totalFolderRetries} totales",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF25D366).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("$whatsappCount programados", color = Color(0xFF81C784), fontSize = 11.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = GoldAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Llamada", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("$callCount programados", color = GoldAccent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderStatItem(batch: BatchSummary) {
    val rate = if (batch.total > 0) ((batch.interested.toFloat() / batch.total) * 100).toInt() else 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = batch.importBatchName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "$rate% Éxito",
                    color = GoldAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { (rate / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = GoldAccent,
                trackColor = SurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${batch.total} contactos · ${batch.interested} interesados · ${batch.notInterested} no interesados",
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun DailyStatItem(stat: DailyStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = stat.date, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = "${stat.totalAttempts} llamadas realizadas", color = OnSurfaceMuted, fontSize = 12.sp)
            }
            
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
            ) {
                Text(
                    text = "👍 ${stat.interestedCount} Int.",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color(0xFF81C784),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
