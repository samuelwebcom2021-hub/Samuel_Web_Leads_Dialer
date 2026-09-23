package com.tuempresa.autodialer.ui.call.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.data.CallSessionEntity
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.ui.components.ButtonState
import com.tuempresa.autodialer.ui.components.CrmActionButton
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallDataPanel(
    session: CallSessionEntity,
    contact: ContactEntity?,
    alternateNumber: String,
    whatsappNumber: String,
    notes: String,
    onDataChange: (String, String, String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    var buttonState by remember { mutableStateOf(ButtonState.NORMAL) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Datos comerciales del contacto" },
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantDark
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ZONA DE DATOS DEL NEGOCIO
            if (contact != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DATOS DEL NEGOCIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    if (contact.rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${contact.rating} (${contact.reviewCount ?: 0})",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                if (!contact.websiteRaw.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = contact.websiteRaw,
                            color = Color(0xFF81D4FA),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics { contentDescription = if (expanded) "Colapsar datos" else "Ver datos completos" },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAPTURA DE DATOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = GoldAccent
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Campo Número Directo / Dueño con Teclado Numérico Telefónico
                    OutlinedTextField(
                        value = alternateNumber,
                        onValueChange = { 
                            onDataChange(it, whatsappNumber, notes)
                            onSaveClick()
                        },
                        label = { Text("Número Directo / Dueño") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Campo número alternativo" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedLabelColor = GoldAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Campo WhatsApp de Seguimiento con Teclado Numérico Telefónico
                    OutlinedTextField(
                        value = whatsappNumber,
                        onValueChange = { 
                            onDataChange(alternateNumber, it, notes)
                            onSaveClick()
                        },
                        label = { Text("WhatsApp de seguimiento") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Campo número WhatsApp" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedLabelColor = GoldAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { 
                            onDataChange(alternateNumber, whatsappNumber, it)
                            onSaveClick()
                        },
                        label = { Text("Notas de la llamada") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Campo notas" },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedLabelColor = GoldAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    CrmActionButton(
                        text = if (buttonState == ButtonState.SUCCESS) "GUARDADO ✓" else "GUARDAR AHORA",
                        onClick = {
                            buttonState = ButtonState.LOADING
                            onSaveClick()
                            scope.launch {
                                delay(300)
                                buttonState = ButtonState.SUCCESS
                                delay(1500)
                                buttonState = ButtonState.NORMAL
                            }
                        },
                        state = buttonState,
                        modifier = Modifier.fillMaxWidth(),
                        contentDescription = "Botón para guardar información capturada"
                    )

                    // Guardado automático continuo (Debounce)
                    LaunchedEffect(alternateNumber, whatsappNumber, notes) {
                        delay(1000)
                        onSaveClick()
                    }
                }
            }
        }
    }
}
