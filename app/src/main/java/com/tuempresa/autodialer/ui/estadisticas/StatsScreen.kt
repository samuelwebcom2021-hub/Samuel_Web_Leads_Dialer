package com.tuempresa.autodialer.ui.estadisticas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuempresa.autodialer.data.DailyStat
import com.tuempresa.autodialer.ui.ContactsViewModel
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark

@Composable
fun StatsScreen(viewModel: ContactsViewModel = viewModel()) {
    val dailyStats by viewModel.dailyStats.observeAsState(emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        Text(
            text = "Estadísticas",
            style = MaterialTheme.typography.headlineLarge,
            color = GoldAccent,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Resumen de actividad comercial",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (dailyStats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No hay datos para mostrar", color = OnSurfaceMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                val totalCalls = dailyStats.sumOf { it.totalAttempts }
                val totalInterested = dailyStats.sumOf { it.interestedCount }
                
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(
                            label = "Llamadas",
                            value = totalCalls.toString(),
                            icon = Icons.Default.Call,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Interesados",
                            value = totalInterested.toString(),
                            icon = Icons.Default.ThumbUp,
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                item {
                    Text("ACTIVIDAD RECIENTE", color = GoldAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                items(dailyStats.reversed()) { stat ->
                    DailyStatItem(stat)
                }
            }
        }
    }
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = OnSurfaceMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun DailyStatItem(stat: DailyStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = stat.date, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = "${stat.totalAttempts} llamadas realizadas", color = OnSurfaceMuted, fontSize = 12.sp)
            }
            
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "${stat.interestedCount} Int.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFF81C784),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
