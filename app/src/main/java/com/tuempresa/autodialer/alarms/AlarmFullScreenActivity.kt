package com.tuempresa.autodialer.alarms

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuempresa.autodialer.ui.theme.AutoDialerTheme
import com.tuempresa.autodialer.ui.theme.GoldAccent

class AlarmFullScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración para mostrar sobre la pantalla de bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val agendaId = intent.getLongExtra("agenda_id", -1L)
        val title = intent.getStringExtra("title") ?: "Recordatorio"
        val reason = intent.getStringExtra("reason") ?: "Es hora de realizar el seguimiento"

        setContent {
            AutoDialerTheme {
                AlarmScreen(
                    title = title,
                    reason = reason,
                    onStop = {
                        ReminderAlarmManager.stopReminder(this, agendaId)
                        finish()
                    },
                    onSnooze = {
                        ReminderAlarmManager.snoozeReminder(this, agendaId)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun AlarmScreen(
    title: String,
    reason: String,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = reason,
                color = GoldAccent.copy(alpha = 0.8f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)), // DangerRed
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("DETENER", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                shape = MaterialTheme.shapes.large,
                border = androidx.compose.foundation.BorderStroke(2.dp, GoldAccent)
            ) {
                Icon(Icons.Default.Snooze, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("POSPONER 10 MIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
