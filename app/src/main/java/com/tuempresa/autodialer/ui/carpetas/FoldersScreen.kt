package com.tuempresa.autodialer.ui.carpetas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.data.BatchSummary
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.OnSurfaceMuted
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark

import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.tuempresa.autodialer.R

@Composable
fun FoldersScreen(
    batches: List<BatchSummary>,
    onFolderClick: (Long) -> Unit,
    onDeleteFolder: (BatchSummary) -> Unit,
    onImportClick: () -> Unit,
    onGlobalSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val totalContacts = batches.sumOf { it.total }
    val totalInterested = batches.sumOf { it.interested }
    val totalNotInterested = batches.sumOf { it.notInterested }
    val successRate = if (totalContacts > 0) {
        ((totalInterested).toFloat() / totalContacts * 100).toInt()
    } else 0

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Carpetas",
                    style = MaterialTheme.typography.headlineLarge,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gestiona tus listas de contactos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted
                )
            }
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                onGlobalSearch(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar en todas las carpetas...", color = OnSurfaceMuted) },
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

        Spacer(modifier = Modifier.height(16.dp))

        GeneralSummaryCard(
            active = totalContacts,
            interested = totalInterested,
            notInterested = totalNotInterested,
            successRate = successRate
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
            shape = CircleShape
        ) {
            Icon(Icons.Default.Phone, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("+ Nueva importación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mis carpetas",
            style = MaterialTheme.typography.titleMedium,
            color = GoldAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val filteredBatches = if (searchQuery.isBlank()) batches else batches.filter { 
            it.importBatchName.contains(searchQuery, ignoreCase = true)
        }

        if (filteredBatches.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (batches.isEmpty()) "Aún no tienes carpetas" else "No se encontraron carpetas", color = OnSurfaceMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredBatches) { batch ->
                    FolderItem(
                        batch = batch,
                        onClick = { onFolderClick(batch.importBatchId) },
                        onDelete = { onDeleteFolder(batch) }
                    )
                }
            }
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
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large
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
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(text = label, color = OnSurfaceMuted, fontSize = 10.sp)
    }
}

@Composable
fun FolderItem(
    batch: BatchSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = GoldAccent.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = batch.importBatchName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${batch.total} contactos · ${batch.interested} interesados",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar carpeta",
                    tint = Color(0xFFD32F2F).copy(alpha = 0.7f)
                )
            }
        }
    }
}
