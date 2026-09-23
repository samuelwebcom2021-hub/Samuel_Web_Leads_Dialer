package com.tuempresa.autodialer.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.tuempresa.autodialer.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Gestión de autenticación con Google usando Credential Manager (moderno API).
 * Reutilizado exclusivamente como mecanismo de login/identidad para Firebase.
 */
class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    private val SERVER_CLIENT_ID by lazy {
        context.getString(R.string.default_web_client_id)
    }

    suspend fun signIn(activity: Activity): Result<GoogleIdTokenCredential> = withContext(Dispatchers.IO) {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            if (credential is GoogleIdTokenCredential) {
                // Paso crítico: Vincular con Firebase
                val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                firebaseAuth.signInWithCredential(firebaseCredential).await()
                
                Result.success(credential)
            } else {
                Result.failure(Exception("Tipo de credencial no esperado"))
            }
        } catch (e: Exception) {
            val message = if (e.message?.contains("NEED_REMOTE_CONSENT") == true) {
                "Se requiere consentimiento remoto (revisa las notificaciones en tu otro dispositivo o la configuración de seguridad de Google)."
            } else {
                e.message
            }
            Log.e("GoogleAuthManager", "Error al iniciar sesión: $message")
            Result.failure(Exception(message, e))
        }
    }

    suspend fun signOut() {
        try {
            firebaseAuth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Error al cerrar sesión: ${e.message}")
        }
    }
}
