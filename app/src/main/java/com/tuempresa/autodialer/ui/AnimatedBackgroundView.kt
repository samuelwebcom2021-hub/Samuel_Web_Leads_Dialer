package com.tuempresa.autodialer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tuempresa.autodialer.R
import java.util.Random

/**
 * Vista personalizada para un fondo animado premium con partículas doradas flotantes.
 * Es eficiente: pausa el dibujo cuando la Activity no está en primer plano.
 */
class AnimatedBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), DefaultLifecycleObserver {

    private val random = Random()
    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private var isPaused = false
    private val goldColors = listOf(
        ContextCompat.getColor(context, R.color.goldAccent),
        ContextCompat.getColor(context, R.color.goldLight),
        Color.parseColor("#FFD700"), // Oro puro
        Color.parseColor("#B8860B")  // Oro viejo
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particles.clear()
        // Aumentamos un poco la densidad para que se vea más vivo
        val particleCount = (w * h / 15000).coerceIn(40, 120)
        repeat(particleCount) {
            particles.add(Particle(w.toFloat(), h.toFloat(), random, goldColors))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        particles.forEach { p ->
            p.move() 
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt()
            
            // Dibujar un pequeño resplandor para las partículas más grandes (efecto premium)
            if (p.radius > 4f) {
                particlePaint.setShadowLayer(p.radius * 2, 0f, 0f, p.color)
            } else {
                particlePaint.clearShadowLayer()
            }
            
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
        }

        if (!isPaused) invalidate()
    }

    // --- Lifecycle Control ---

    override fun onResume(owner: LifecycleOwner) {
        isPaused = false
        invalidate()
    }

    override fun onPause(owner: LifecycleOwner) {
        isPaused = true
    }

    private class Particle(private val screenW: Float, private val screenH: Float, random: Random, colors: List<Int>) {
        var x = random.nextFloat() * screenW
        var y = random.nextFloat() * screenH
        
        // Variedad de tamaños: algunas muy pequeñas (lejos) y otras más grandes (cerca)
        var radius = 1.5f + random.nextFloat() * 5f
        
        // Movimiento más dinámico: las grandes se mueven más rápido (efecto profundidad)
        private val baseSpeed = 0.3f + (radius / 6f)
        var speedX = (-1f + random.nextFloat() * 2f) * baseSpeed
        var speedY = (-1f + random.nextFloat() * 2f) * baseSpeed
        
        var alpha = 0.1f + random.nextFloat() * 0.7f
        var alphaDir = (0.002f + random.nextFloat() * 0.005f) * if (random.nextBoolean()) 1 else -1
        
        val color = colors[random.nextInt(colors.size)]

        fun move() {
            x += speedX
            y += speedY
            alpha += alphaDir
            
            // Twinkle effect (parpadeo)
            if (alpha >= 0.9f || alpha <= 0.1f) alphaDir = -alphaDir
            
            // Reaparecer por el lado opuesto de forma fluida
            if (x < -20) x = screenW + 20
            if (x > screenW + 20) x = -20f
            if (y < -20) y = screenH + 20
            if (y > screenH + 20) y = -20f
        }
    }
}
