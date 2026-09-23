package com.tuempresa.autodialer.ui.call.components

import android.telecom.CallAudioState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresApi
import android.os.Build
import com.tuempresa.autodialer.dialer.AutoDialerInCallService
import com.tuempresa.autodialer.ui.theme.GoldAccent
import com.tuempresa.autodialer.ui.theme.SurfaceVariantDark

@Composable
fun CallControls(
    onKeypadClick: () -> Unit,
    onHangupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val service by AutoDialerInCallService.activeInCallService.collectAsState()
    val audioState by (service?.audioState?.collectAsState() ?: remember { 
        mutableStateOf(null) 
    })

    val isMuted = audioState?.isMuted ?: false
    val isSpeaker = audioState?.route == CallAudioState.ROUTE_SPEAKER

    Column(modifier = modifier.fillMaxWidth()) {
        // ZONA B: CONTROLES TELEFÓNICOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Toggle
            IconButton(
                onClick = { service?.setMute(!isMuted) },
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = if (isMuted) "Desactivar silencio" else "Silenciar micrófono" },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isMuted) GoldAccent else SurfaceVariantDark,
                    contentColor = if (isMuted) Color.Black else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Keypad Toggle
            IconButton(
                onClick = onKeypadClick,
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = "Mostrar teclado numérico" },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = SurfaceVariantDark,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Speaker Toggle
            IconButton(
                onClick = { service?.setSpeaker(!isSpeaker) },
                modifier = Modifier
                    .size(64.dp)
                    .semantics { contentDescription = if (isSpeaker) "Usar auricular" else "Activar altavoz" },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSpeaker) GoldAccent else SurfaceVariantDark,
                    contentColor = if (isSpeaker) Color.Black else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ZONA D: ACCIÓN PRINCIPAL (Finalizar)
        Button(
            onClick = onHangupClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = "Finalizar llamada" },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F), // Rojo intenso
                contentColor = Color.White
            ),
            shape = CircleShape
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("FINALIZAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
