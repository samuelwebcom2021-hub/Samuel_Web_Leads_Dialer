package com.tuempresa.autodialer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tuempresa.autodialer.App
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.auth.GoogleAuthManager
import kotlinx.coroutines.launch

/**
 * Solo aparece la primera vez que se abre la app. Iniciar sesión aquí es un atajo, NO un
 * requisito — "Ahora no" te deja seguir igual; el inicio de sesión sigue disponible en
 * Ajustes cuando quieras, tal como en el resto de la app.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var authManager: GoogleAuthManager
    private lateinit var animatedBackground: AnimatedBackgroundView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        authManager = GoogleAuthManager(this)

        animatedBackground = findViewById(R.id.animatedBackground)
        lifecycle.addObserver(animatedBackground)

        findViewById<android.view.View>(R.id.onboardingSignInButton).setOnClickListener {
            onSignInClicked()
        }
        findViewById<android.view.View>(R.id.onboardingSkipButton).setOnClickListener {
            finishOnboarding()
        }
    }

    private fun onSignInClicked() {
        lifecycleScope.launch {
            val result = authManager.signIn(this@OnboardingActivity)
            result.onSuccess { credential ->
                (application as App).settingsRepository.updateGoogleAccountEmail(credential.id)
                Toast.makeText(this@OnboardingActivity, "Conectado como ${credential.id}", Toast.LENGTH_LONG).show()
            }
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            (application as App).settingsRepository.updateHasSeenOnboarding(true)
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }
}
