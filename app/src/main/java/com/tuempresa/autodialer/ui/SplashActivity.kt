package com.tuempresa.autodialer.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pantalla de bienvenida. La primera vez que se abre la app es más elaborada (logo, y luego
 * el nombre "escribiéndose"); de ahí en adelante es un splash bien rápido (medio segundo o
 * menos) para ir directo al grano — así lo pediste.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var animatedBackground: AnimatedBackgroundView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RF-2: Chequeo bloqueante ultra-rápido para evitar parpadeo si ya se vio el onboarding
        val hasSeenOnboarding = try {
            runBlocking {
                withTimeoutOrNull(300) {
                    (application as App).settingsRepository.settingsFlow.first().hasSeenOnboarding
                } ?: false
            }
        } catch (e: Exception) {
            false
        }

        if (hasSeenOnboarding) {
            goNext(true)
            return
        }

        setContentView(R.layout.activity_splash)

        animatedBackground = findViewById(R.id.animatedBackground)
        lifecycle.addObserver(animatedBackground)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val nameText = findViewById<TextView>(R.id.splashAppName)
        val tagline = findViewById<TextView>(R.id.splashTagline)
        
        runElaborateAnimation(logo, nameText, tagline)
    }

    private fun runElaborateAnimation(logo: ImageView, nameText: TextView, tagline: TextView) {
        val fullName = getString(R.string.app_name)
        nameText.text = ""
        logo.alpha = 0f
        logo.scaleX = 0.2f
        logo.scaleY = 0.2f
        
        // 1. Reveal del logo con escala y resplandor (overshoot)
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(1000)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                // 2. Aparece el nombre letra por letra
                handler.postDelayed({ typewriter(nameText, fullName, 0) {
                    // 3. Aparece el tagline al final
                    tagline.visibility = View.VISIBLE
                    tagline.alpha = 0f
                    tagline.animate().alpha(1f).setDuration(800).withEndAction {
                        handler.postDelayed({ goNext() }, 1200)
                    }.start()
                } }, 200)
            }
            .start()
    }

    private fun typewriter(textView: TextView, fullText: String, index: Int, onComplete: () -> Unit) {
        if (index > fullText.length) {
            onComplete()
            return
        }
        textView.text = fullText.substring(0, index)
        val delay = if (index > 0 && fullText[index - 1] == ' ') 150L else 60L
        handler.postDelayed({ typewriter(textView, fullText, index + 1, onComplete) }, delay)
    }

    private fun goNext(hasSeenOnboarding: Boolean) {
        val next = if (hasSeenOnboarding) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, next))
        finish()
        // RF-2: Quitar transición para que el salto sea instantáneo
        if (hasSeenOnboarding) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun goNext() {
        val settings = (application as App).settingsState.value
        goNext(settings.hasSeenOnboarding)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
