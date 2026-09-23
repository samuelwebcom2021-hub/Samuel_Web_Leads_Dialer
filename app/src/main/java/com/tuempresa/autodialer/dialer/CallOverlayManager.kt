package com.tuempresa.autodialer.dialer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.tuempresa.autodialer.R
import com.tuempresa.autodialer.data.ContactEntity
import com.tuempresa.autodialer.excel.WebsiteClassifier

/**
 * Tarjeta flotante (overlay del sistema) que se dibuja ENCIMA de la app de llamadas de
 * Android mientras una llamada está en curso, mostrando los datos del negocio — sin
 * necesidad de reemplazar el marcador nativo del teléfono.
 *
 * Requiere el permiso especial "Mostrar sobre otras apps" (SYSTEM_ALERT_WINDOW), que el
 * usuario concede manualmente desde Ajustes del sistema (no es un permiso normal de los que
 * se piden con un diálogo). Si no está concedido, simplemente no se muestra nada y la llamada
 * funciona igual — es un extra, no un requisito.
 */
class CallOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var startTimeMillis = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(contact: ContactEntity) {
        if (!canDrawOverlays() || overlayView != null) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_call_info, null)
        view.findViewById<TextView>(R.id.overlayBusiness).text =
            contact.businessName.ifBlank { "(sin nombre de negocio)" }
        view.findViewById<TextView>(R.id.overlayPhone).text = contact.phoneNumber

        val websiteType = contact.websiteType?.let { runCatching { WebsiteClassifier.Type.valueOf(it) }.getOrNull() }
        view.findViewById<TextView>(R.id.overlayWebsite).text = when (websiteType) {
            WebsiteClassifier.Type.DOMINIO_PROPIO -> "🌐 Sitio propio"
            WebsiteClassifier.Type.RED_SOCIAL -> "📱 Red social"
            WebsiteClassifier.Type.CONSTRUCTOR_WEB -> "🛠️ Constructor web"
            WebsiteClassifier.Type.UNRECOGNIZED -> "Enlace sin identificar"
            else -> "Sin sitio web"
        }
        view.findViewById<TextView>(R.id.overlayRating).text =
            if (contact.rating != null || contact.reviewCount != null) {
                "⭐ ${contact.rating ?: "?"} (${contact.reviewCount ?: 0})"
            } else ""
        view.findViewById<TextView>(R.id.overlayAttempt).text = "Intento ${contact.attemptCount}/2"

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        try {
            windowManager.addView(view, params)
            overlayView = view
            startTimer(view)
        } catch (e: Exception) {
            // Algunos fabricantes restringen los overlays; si falla, seguimos sin la tarjeta.
        }
    }

    fun hide() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun startTimer(view: View) {
        startTimeMillis = System.currentTimeMillis()
        val timerText = view.findViewById<TextView>(R.id.overlayTimer)
        tickRunnable = object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000
                timerText.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tickRunnable!!)
    }
}
