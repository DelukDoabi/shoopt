package com.dedoware.shoopt.activities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.dedoware.shoopt.R
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Simple ConfettiView: dessine des particules (rectangles colorés) qui tombent et se dispersent.
 * Usage: appeler start(durationMs) pour lancer l'animation.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private data class Particle(
        var x: Float,
        var y: Float,
        val size: Float,
        val color: Int,
        var vx: Float,
        var vy: Float,
        val lifetime: Long,
        var age: Long = 0L,
        val rot: Float = 0f
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<Particle>()
    private var running = false
    private var startTime = 0L
    private var duration = 1500L
    private val rand = Random(System.currentTimeMillis())
    private val colors by lazy {
        listOf(
            ContextCompat.getColor(context, R.color.primary_color),
            ContextCompat.getColor(context, R.color.secondary_color),
            ContextCompat.getColor(context, R.color.teal_200),
            0xFFFFC107.toInt(), // amber
            0xFFFF5252.toInt()  // red accent
        )
    }

    /** Démarre l'animation pendant [durationMs] millisecondes. */
    fun start(durationMs: Long = 1500L) {
        if (running) return
        duration = durationMs
        generateParticles()
        running = true
        startTime = SystemClock.uptimeMillis()
        visibility = VISIBLE
        invalidate()
    }

    private fun generateParticles() {
        particles.clear()
        val count = 40
        val w = width.coerceAtLeast(1)
        for (i in 0 until count) {
            val size = (8 + rand.nextInt(16)).toFloat()
            val x = rand.nextInt(w).toFloat()
            val y = -rand.nextInt(200).toFloat()
            val color = colors[rand.nextInt(colors.size)]
            val vx = (rand.nextFloat() - 0.5f) * 300f / 1000f // px per ms
            val vy = (200 + rand.nextInt(400)).toFloat() / 1000f
            val life = 1000L + rand.nextLong(1000L)
            particles.add(Particle(x, y, size, color, vx, vy, life))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running) return

        val now = SystemClock.uptimeMillis()
        val dt = now - startTime
        var anyAlive = false

        for (p in particles) {
            if (p.age < p.lifetime) {
                anyAlive = true
                // update
                p.age = min(p.lifetime, p.age + (now - startTime).coerceAtMost(50L))
                // simple physics step proportional to fraction of lifetime
                val t = (now - startTime).toFloat()
                p.x += p.vx * 16f
                p.y += p.vy * 16f
                // gravity effect
                p.vy += 0.0008f * 16f

                val alpha = 1f - (p.age.toFloat() / p.lifetime.toFloat())
                paint.color = p.color
                paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)

                val left = p.x
                val top = p.y
                val right = p.x + p.size
                val bottom = p.y + p.size
                canvas.drawRect(RectF(left, top, right, bottom), paint)
            }
        }

        // schedule next frame or stop
        if (anyAlive && (now - startTime) < duration + 1000L) {
            postInvalidateOnAnimation()
        } else {
            running = false
            // cleanup
            particles.clear()
            visibility = GONE
        }
    }
}
