package com.dedoware.shoopt.ui.effects

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.dedoware.shoopt.R
import kotlin.math.*
import kotlin.random.Random

/**
 * Vue personnalisée pour créer un effet de particules sophistiqué lors des félicitations
 */
class CelebrationParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var animationProgress = 0f

    // Couleurs modernes pour les particules
    private val particleColors = arrayOf(
        ContextCompat.getColor(context, R.color.primary_color),
        ContextCompat.getColor(context, R.color.secondary_color),
        Color.parseColor("#FFD700"), // Or
        Color.parseColor("#FF6B35"), // Orange vif
        Color.parseColor("#4ECDC4"), // Turquoise
        Color.parseColor("#45B7D1"), // Bleu ciel
        Color.parseColor("#96CEB4"), // Vert menthe
        Color.parseColor("#FFEAA7")  // Jaune doux
    )

    init {
        // Transparent par défaut
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun startCelebration() {
        createParticles()

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun createParticles() {
        particles.clear()
        val particleCount = 50

        repeat(particleCount) {
            particles.add(createRandomParticle())
        }
    }

    private fun createRandomParticle(): Particle {
        val centerX = width / 2f
        val centerY = height / 2f

        // Position de départ près du centre
        val startX = centerX + Random.nextFloat() * 100 - 50
        val startY = centerY + Random.nextFloat() * 100 - 50

        // Vélocité aléatoire vers l'extérieur
        val angle = Random.nextFloat() * 2 * PI
        val speed = Random.nextFloat() * 300 + 100
        val velocityX = cos(angle).toFloat() * speed
        val velocityY = sin(angle).toFloat() * speed

        return Particle(
            startPosition = PointF(startX, startY),
            velocity = PointF(velocityX, velocityY),
            color = particleColors[Random.nextInt(particleColors.size)],
            size = Random.nextFloat() * 8 + 4,
            rotationSpeed = Random.nextFloat() * 360 - 180,
            gravity = Random.nextFloat() * 50 + 25,
            lifetime = Random.nextFloat() * 0.5f + 0.5f,
            shape = ParticleShape.values()[Random.nextInt(ParticleShape.values().size)]
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (animationProgress == 0f) return

        particles.forEach { particle ->
            drawParticle(canvas, particle)
        }
    }

    private fun drawParticle(canvas: Canvas, particle: Particle) {
        val time = animationProgress

        // Calculer la position actuelle
        val x = particle.startPosition.x + particle.velocity.x * time
        val y = particle.startPosition.y + particle.velocity.y * time + 0.5f * particle.gravity * time * time

        // Calculer l'alpha basé sur le lifetime
        val normalizedLifetime = (time / particle.lifetime).coerceIn(0f, 1f)
        val alpha = (255 * (1f - normalizedLifetime)).toInt().coerceIn(0, 255)

        // Calculer la taille avec un effet de rétrécissement
        val currentSize = particle.size * (1f - normalizedLifetime * 0.3f)

        // Calculer la rotation
        val rotation = particle.rotationSpeed * time

        paint.color = particle.color
        paint.alpha = alpha

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)

        when (particle.shape) {
            ParticleShape.CIRCLE -> {
                canvas.drawCircle(0f, 0f, currentSize, paint)
            }
            ParticleShape.STAR -> {
                drawStar(canvas, currentSize)
            }
            ParticleShape.HEART -> {
                drawHeart(canvas, currentSize)
            }
            ParticleShape.DIAMOND -> {
                drawDiamond(canvas, currentSize)
            }
        }

        canvas.restore()
    }

    private fun drawStar(canvas: Canvas, size: Float) {
        val path = Path()
        val outerRadius = size
        val innerRadius = size * 0.5f
        val centerX = 0f
        val centerY = 0f

        for (i in 0 until 10) {
            val angle = (i * 36 - 90) * PI / 180
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHeart(canvas: Canvas, size: Float) {
        val path = Path()
        val scale = size / 10f

        path.moveTo(0f, 3f * scale)
        path.cubicTo(-3f * scale, 0f, -6f * scale, 0f, -6f * scale, -3f * scale)
        path.cubicTo(-6f * scale, -6f * scale, -3f * scale, -6f * scale, 0f, -3f * scale)
        path.cubicTo(3f * scale, -6f * scale, 6f * scale, -6f * scale, 6f * scale, -3f * scale)
        path.cubicTo(6f * scale, 0f, 3f * scale, 0f, 0f, 3f * scale)
        path.close()

        canvas.drawPath(path, paint)
    }

    private fun drawDiamond(canvas: Canvas, size: Float) {
        val path = Path()
        path.moveTo(0f, -size)
        path.lineTo(size * 0.7f, 0f)
        path.lineTo(0f, size)
        path.lineTo(-size * 0.7f, 0f)
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    data class Particle(
        val startPosition: PointF,
        val velocity: PointF,
        val color: Int,
        val size: Float,
        val rotationSpeed: Float,
        val gravity: Float,
        val lifetime: Float,
        val shape: ParticleShape
    )

    enum class ParticleShape {
        CIRCLE, STAR, HEART, DIAMOND
    }
}
