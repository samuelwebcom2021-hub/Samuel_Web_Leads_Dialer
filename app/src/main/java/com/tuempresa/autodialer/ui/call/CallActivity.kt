package com.tuempresa.autodialer.ui.call

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.tuempresa.autodialer.data.CallState
import com.tuempresa.autodialer.dialer.DialerEvents
import com.tuempresa.autodialer.ui.AnimatedBackgroundView
import com.tuempresa.autodialer.ui.theme.AutoDialerTheme

class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configurar para mostrar sobre la pantalla de bloqueo y encender pantalla
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContent {
            AutoDialerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { context ->
                            AnimatedBackgroundView(context).also {
                                lifecycle.addObserver(it)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    CallScreen()
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val session = DialerEvents.session.value
        if (session != null && (session.state == CallState.ACTIVE || session.state == CallState.DIALING || session.state == CallState.RINGING)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(9, 16))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    // Si el dispositivo no soporta PiP, la notificación interactiva se encarga
                }
            }
        }
    }

    fun finishCallActivity() {
        if (!isFinishing && !isDestroyed) {
            try {
                val intent = Intent(this, com.tuempresa.autodialer.ui.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Ignores errors if main activity cannot be brought to front
            }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
