package com.dedoware.shoopt.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.createBitmap
import com.dedoware.shoopt.R
import com.google.android.material.button.MaterialButton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Overlay moderne avec spotlight animé et tooltip adaptatif
 * Version améliorée avec animations premium et effets visuels sophistiqués
 */
class ModernGuideOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ModernGuideOverlay"
    }

    private var step: ModernGuideSystem.GuideStep? = null
    private var onDismiss: (() -> Unit)? = null

    // Secondary constructor for programmatic usage
    constructor(
        context: Context,
        guideStep: ModernGuideSystem.GuideStep,
        dismissCallback: () -> Unit
    ) : this(context) {
        this.step = guideStep
        this.onDismiss = dismissCallback
        setupOverlay()
    }

    private var spotlightAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var breathingAnimator: AnimatorSet? = null
    private var tooltipCard: CardView? = null
    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Spotlight properties
    private var spotlightRadius = 0f
    private var maxSpotlightRadius = 0f
    private var spotlightCenterX = 0f
    private var spotlightCenterY = 0f
    private var targetRect = RectF()
    private var pulseRadius = 0f
    private var pulseAlpha = 0f

    // Animation state
    private var isAnimating = false
    private var hasBeenShown = false

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (step != null) {
            setupOverlay()
        }
    }

    private fun setupOverlay() {
        val currentStep = step ?: return

        // Configuration du background semi-transparent avec gradient
        setBackgroundColor(0x80000000.toInt())

        // Créer le tooltip avec animation d'entrée différée
        createTooltip()

        // Calculer la position du spotlight si nécessaire
        if (currentStep.targetView != null && currentStep.spotlightType != ModernGuideSystem.SpotlightType.NONE) {
            post { calculateSpotlightPosition() }
        }
    }

    private fun createTooltip() {
        val currentStep = step ?: return

        // Inflate le layout du tooltip moderne
        val tooltipView = LayoutInflater.from(context)
            .inflate(R.layout.modern_tooltip_layout, this, false)

        tooltipCard = tooltipView.findViewById(R.id.tooltip_card)
        val titleText = tooltipView.findViewById<TextView>(R.id.tooltip_title)
        val descriptionText = tooltipView.findViewById<TextView>(R.id.tooltip_description)
        val confirmButton = tooltipView.findViewById<View>(R.id.tooltip_confirm_button)
        val confirmText = tooltipView.findViewById<TextView>(R.id.tooltip_confirm_text)
        val skipButton = tooltipView.findViewById<TextView>(R.id.tooltip_skip_button)

        // Configurer le contenu avec les données de step
        titleText.text = context.getString(currentStep.titleResId)
        descriptionText.text = context.getString(currentStep.descriptionResId)

        // Gérer les boutons avec animations
        if (currentStep.showConfirmButton) {
            confirmButton.visibility = VISIBLE
            confirmText?.text = context.getString(currentStep.confirmButtonTextResId)
            confirmButton.setOnClickListener {
                animateButtonPress(confirmButton) {
                    currentStep.onConfirm?.invoke()
                    onDismiss?.invoke()
                }
            }
        } else {
            confirmButton.visibility = GONE
        }

        // Bouton skip avec animation
        skipButton.setOnClickListener {
            animateButtonPress(skipButton) {
                onDismiss?.invoke()
            }
        }

        // Ajouter le tooltip avec animation d'entrée différée
        tooltipCard?.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }

        addView(tooltipView)

        // CORRECTION CRITIQUE : Calculer le spotlight AVANT de positionner le tooltip
        if (currentStep.targetView != null && currentStep.spotlightType != ModernGuideSystem.SpotlightType.NONE) {
            post {
                // Forcer le calcul du spotlight en premier
                calculateSpotlightPosition()
                // Puis positionner le tooltip avec les bonnes coordonnées
                positionTooltip()
            }
        } else {
            // Pas de spotlight, positionner directement le tooltip
            post { positionTooltip() }
        }
    }

    private fun calculateSpotlightPosition() {
        val currentStep = step ?: return
        currentStep.targetView?.let { target ->
            try {
                val location = IntArray(2)
                target.getLocationInWindow(location)

                // Ajuster les coordonnées par rapport à l'overlay
                val overlayLocation = IntArray(2)
                getLocationInWindow(overlayLocation)

                val targetX = location[0] - overlayLocation[0]
                val targetY = location[1] - overlayLocation[1]

                targetRect.set(
                    targetX.toFloat(),
                    targetY.toFloat(),
                    (targetX + target.width).toFloat(),
                    (targetY + target.height).toFloat()
                )

                Log.d(TAG, "SPOTLIGHT: Target dimensions = ${target.width}x${target.height}")
                Log.d(TAG, "SPOTLIGHT: TargetRect = $targetRect")

                when (currentStep.spotlightType) {
                    ModernGuideSystem.SpotlightType.CIRCLE -> {
                        spotlightCenterX = targetRect.centerX()
                        spotlightCenterY = targetRect.centerY()
                        // CORRECTION : Spotlight BEAUCOUP plus grand pour couvrir toute la carte
                        val maxDimension = maxOf(target.width, target.height)
                        // Calculer un rayon qui couvre TOUTE la cible + marge généreuse
                        val diagonalRadius = kotlin.math.sqrt((target.width * target.width + target.height * target.height).toDouble()).toFloat() / 2f
                        maxSpotlightRadius = diagonalRadius + 80f // Marge généreuse de 80px
                        Log.d(TAG, "SPOTLIGHT: Circle at (${spotlightCenterX}, ${spotlightCenterY}) with ENHANCED radius ${maxSpotlightRadius} (was ${(maxDimension / 2f) + 48f})")
                    }
                    ModernGuideSystem.SpotlightType.PULSE -> {
                        spotlightCenterX = targetRect.centerX()
                        spotlightCenterY = targetRect.centerY()
                        val maxDimension = maxOf(target.width, target.height)
                        // Même logique pour le pulse
                        val diagonalRadius = kotlin.math.sqrt((target.width * target.width + target.height * target.height).toDouble()).toFloat() / 2f
                        maxSpotlightRadius = diagonalRadius + 80f
                        startPulseAnimation()
                        Log.d(TAG, "SPOTLIGHT: Pulse at (${spotlightCenterX}, ${spotlightCenterY}) with ENHANCED radius ${maxSpotlightRadius}")
                    }
                    ModernGuideSystem.SpotlightType.ROUNDED_RECT -> {
                        spotlightCenterX = targetRect.centerX()
                        spotlightCenterY = targetRect.centerY()
                        Log.d(TAG, "SPOTLIGHT: RoundedRect centered at (${spotlightCenterX}, ${spotlightCenterY})")
                    }
                    ModernGuideSystem.SpotlightType.NONE -> {
                        Log.d(TAG, "SPOTLIGHT: None")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors du calcul de position spotlight: ${e.message}")
            }
        }
    }

    private fun positionTooltip() {
        val currentStep = step ?: return
        tooltipCard?.let { card ->
            val layoutParams = card.layoutParams as LayoutParams

            when (currentStep.tooltipPosition) {
                ModernGuideSystem.TooltipPosition.AUTO -> {
                    positionTooltipAuto(layoutParams)
                }
                ModernGuideSystem.TooltipPosition.CENTER -> {
                    layoutParams.gravity = Gravity.CENTER
                }
                ModernGuideSystem.TooltipPosition.TOP -> {
                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    layoutParams.topMargin = 120
                }
                ModernGuideSystem.TooltipPosition.BOTTOM -> {
                    layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    layoutParams.bottomMargin = 120
                }
                ModernGuideSystem.TooltipPosition.LEFT -> {
                    layoutParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams.marginStart = 60
                }
                ModernGuideSystem.TooltipPosition.RIGHT -> {
                    layoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    layoutParams.marginEnd = 60
                }
            }

            card.layoutParams = layoutParams
        }
    }

    private fun positionTooltipAuto(layoutParams: LayoutParams) {
        val currentStep = step ?: return

        // DEBUG : Système de positionnement avec logs IMMEDIATS dans Android Studio
        try {
            // Réinitialiser tous les marges
            layoutParams.topMargin = 0
            layoutParams.bottomMargin = 0
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0

            val density = context.resources.displayMetrics.density
            val marginPx = (24 * density).toInt()
            val tooltipHeightPx = (280 * density).toInt()
            val safeMarginPx = (80 * density).toInt()

            Log.d(TAG, "=== DEBUG TOOLTIP POSITIONING START ===")
            Log.d(TAG, "Density: $density")
            Log.d(TAG, "MarginPx: $marginPx")
            Log.d(TAG, "TooltipHeightPx: $tooltipHeightPx")
            Log.d(TAG, "SafeMarginPx: $safeMarginPx")

            // Positionnement intelligent selon la cible
            if (currentStep.targetView == null || currentStep.spotlightType == ModernGuideSystem.SpotlightType.NONE) {
                // Pas de cible - centrer
                layoutParams.gravity = Gravity.CENTER
                layoutParams.leftMargin = marginPx
                layoutParams.rightMargin = marginPx
                Log.d(TAG, "TOOLTIP: CENTER (no target)")
            } else {
                // Calculer les espaces disponibles
                val screenHeight = height
                val screenWidth = width

                Log.d(TAG, "Screen size: ${screenWidth}x${screenHeight}")
                Log.d(TAG, "TargetRect: $targetRect")

                val spaceAbove = targetRect.top
                val spaceBelow = screenHeight - targetRect.bottom
                val spaceLeft = targetRect.left
                val spaceRight = screenWidth - targetRect.right

                val targetCenterY = targetRect.centerY()
                val targetCenterX = targetRect.centerX()

                Log.d(TAG, "Target center: (${targetCenterX}, ${targetCenterY})")
                Log.d(TAG, "Spaces - Above: ${spaceAbove}, Below: ${spaceBelow}, Left: ${spaceLeft}, Right: ${spaceRight}")

                // SIMPLIFICATION : Forcer le positionnement selon zones d'écran UNIQUEMENT
                when {
                    targetCenterY > screenHeight * 0.5f -> {
                        // Cible en BAS d'écran → FORCER tooltip en HAUT
                        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        layoutParams.topMargin = (40 * density).toInt() // Très haut dans l'écran
                        layoutParams.leftMargin = marginPx
                        layoutParams.rightMargin = marginPx
                        Log.d(TAG, "DECISION: FORCE TOP (target below middle) - topMargin=${layoutParams.topMargin}")
                    }

                    else -> {
                        // Cible en HAUT d'écran → FORCER tooltip en BAS
                        layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        layoutParams.bottomMargin = (80 * density).toInt()
                        layoutParams.leftMargin = marginPx
                        layoutParams.rightMargin = marginPx
                        Log.d(TAG, "DECISION: FORCE BOTTOM (target above middle) - bottomMargin=${layoutParams.bottomMargin}")
                    }
                }

                Log.d(TAG, "Final layoutParams: gravity=${layoutParams.gravity}, top=${layoutParams.topMargin}, bottom=${layoutParams.bottomMargin}, left=${layoutParams.leftMargin}, right=${layoutParams.rightMargin}")
            }

            Log.d(TAG, "=== DEBUG TOOLTIP POSITIONING END ===")

        } catch (e: Exception) {
            // Fallback ABSOLUMENT sûr
            layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams.topMargin = (50 * context.resources.displayMetrics.density).toInt()
            layoutParams.bottomMargin = 0
            layoutParams.leftMargin = (24 * context.resources.displayMetrics.density).toInt()
            layoutParams.rightMargin = (24 * context.resources.displayMetrics.density).toInt()
            Log.e(TAG, "TOOLTIP: EMERGENCY FALLBACK TOP - ${e.message}")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentStep = step ?: return
        if (currentStep.spotlightType == ModernGuideSystem.SpotlightType.NONE || currentStep.targetView == null) {
            return
        }

        try {
            drawSpotlightEffect(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du dessin spotlight: ${e.message}")
        }
    }

    private fun drawSpotlightEffect(canvas: Canvas) {
        val currentStep = step ?: return

        // Créer le masque si nécessaire
        if (maskBitmap == null || maskBitmap?.isRecycled == true) {
            maskBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            maskCanvas = Canvas(maskBitmap!!)
        }

        maskCanvas?.let { maskCanvas ->
            // Effacer le masque
            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // Dessiner le fond sombre PLUS FONCÉ pour plus de contraste
            paint.color = 0xAA000000.toInt() // Plus opaque
            paint.style = Paint.Style.FILL
            maskCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // Dessiner le spotlight (zone transparente) PLUS LARGE
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

            when (currentStep.spotlightType) {
                ModernGuideSystem.SpotlightType.CIRCLE -> {
                    // Spotlight plus grand pour bien mettre en évidence
                    val enhancedRadius = spotlightRadius + 20f
                    maskCanvas.drawCircle(spotlightCenterX, spotlightCenterY, enhancedRadius, paint)

                    // Ajouter un effet glow autour du spotlight
                    paint.xfermode = null
                    glowPaint.color = Color.argb(60, 255, 255, 255)
                    glowPaint.style = Paint.Style.STROKE
                    glowPaint.strokeWidth = 12f
                    maskCanvas.drawCircle(spotlightCenterX, spotlightCenterY, enhancedRadius + 6f, glowPaint)

                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                ModernGuideSystem.SpotlightType.PULSE -> {
                    // Spotlight principal plus grand
                    val enhancedRadius = spotlightRadius + 20f
                    maskCanvas.drawCircle(spotlightCenterX, spotlightCenterY, enhancedRadius, paint)

                    // Effet de pulsation avec glow plus visible
                    paint.xfermode = null
                    glowPaint.color = Color.argb((pulseAlpha * 180).toInt(), 255, 255, 255)
                    glowPaint.style = Paint.Style.STROKE
                    glowPaint.strokeWidth = 16f
                    maskCanvas.drawCircle(spotlightCenterX, spotlightCenterY, pulseRadius + 20f, glowPaint)

                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                ModernGuideSystem.SpotlightType.ROUNDED_RECT -> {
                    val padding = 48f // Padding plus important
                    maskCanvas.drawRoundRect(
                        targetRect.left - padding,
                        targetRect.top - padding,
                        targetRect.right + padding,
                        targetRect.bottom + padding,
                        32f, 32f, paint
                    )

                    // Ajouter un effet glow pour le rectangle aussi
                    paint.xfermode = null
                    glowPaint.color = Color.argb(60, 255, 255, 255)
                    glowPaint.style = Paint.Style.STROKE
                    glowPaint.strokeWidth = 8f
                    maskCanvas.drawRoundRect(
                        targetRect.left - padding - 4f,
                        targetRect.top - padding - 4f,
                        targetRect.right + padding + 4f,
                        targetRect.bottom + padding + 4f,
                        36f, 36f, glowPaint
                    )

                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                ModernGuideSystem.SpotlightType.NONE -> {
                    // Pas de spotlight
                }
            }

            paint.xfermode = null

            // Dessiner le masque sur le canvas principal
            canvas.drawBitmap(maskBitmap!!, 0f, 0f, null)
        }
    }

    private fun startEnhancedMascotAnimation(mascotImage: ImageView) {
        try {
            val scaleUpX = ObjectAnimator.ofFloat(mascotImage, "scaleX", 1f, 1.08f)
            val scaleUpY = ObjectAnimator.ofFloat(mascotImage, "scaleY", 1f, 1.08f)
            val scaleDownX = ObjectAnimator.ofFloat(mascotImage, "scaleX", 1.08f, 1f)
            val scaleDownY = ObjectAnimator.ofFloat(mascotImage, "scaleY", 1.08f, 1f)

            // Animation de rotation subtile
            val rotateLeft = ObjectAnimator.ofFloat(mascotImage, "rotation", 0f, -2f)
            val rotateRight = ObjectAnimator.ofFloat(mascotImage, "rotation", -2f, 2f)
            val rotateCenter = ObjectAnimator.ofFloat(mascotImage, "rotation", 2f, 0f)

            val breatheIn = AnimatorSet().apply {
                playTogether(scaleUpX, scaleUpY, rotateLeft)
                duration = 2500
                interpolator = AccelerateDecelerateInterpolator()
            }

            val breatheOut = AnimatorSet().apply {
                playTogether(scaleDownX, scaleDownY, rotateRight)
                duration = 2500
                interpolator = AccelerateDecelerateInterpolator()
            }

            val resetRotation = AnimatorSet().apply {
                play(rotateCenter)
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
            }

            breathingAnimator = AnimatorSet().apply {
                playSequentially(breatheIn, breatheOut, resetRotation)
            }

            breathingAnimator?.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (parent != null && !isAnimating) {
                        postDelayed({ breathingAnimator?.start() }, 500)
                    }
                }
            })

            breathingAnimator?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur animation mascotte: ${e.message}")
        }
    }

    private fun startPulseAnimation() {
        try {
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()

                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    val sineWave = sin(progress * Math.PI * 2).toFloat()

                    pulseRadius = maxSpotlightRadius + (maxSpotlightRadius * 0.3f * sineWave)
                    pulseAlpha = 0.6f * (1f - progress)

                    invalidate()
                }

                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur animation pulse: ${e.message}")
        }
    }

    private fun animateButtonPress(view: View, onComplete: () -> Unit) {
        try {
            view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction { onComplete() }
                        .start()
                }
                .start()
        } catch (e: Exception) {
            onComplete()
        }
    }

    fun show() {
        if (hasBeenShown) return
        hasBeenShown = true

        try {
            isAnimating = true

            // Animation d'entrée de l'overlay
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f

            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(0.8f))
                .withEndAction {
                    isAnimating = false
                    showTooltipWithDelay()
                }
                .start()

            // Animation du spotlight si applicable
            if (step?.spotlightType != ModernGuideSystem.SpotlightType.NONE && maxSpotlightRadius > 0) {
                animateSpotlight()
            }
        } catch (e: Exception) {
            isAnimating = false
            Log.e(TAG, "Erreur animation show: ${e.message}")
        }
    }

    private fun showTooltipWithDelay() {
        tooltipCard?.postDelayed({
            try {
                tooltipCard?.animate()
                    ?.alpha(1f)
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(300)
                    ?.setInterpolator(BounceInterpolator())
                    ?.start()
            } catch (e: Exception) {
                tooltipCard?.apply {
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
        }, 200)
    }

    fun hide(onComplete: () -> Unit) {
        try {
            isAnimating = true

            animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    cleanupAnimations()
                    onComplete()
                }
                .start()
        } catch (e: Exception) {
            cleanupAnimations()
            onComplete()
        }
    }

    private fun animateSpotlight() {
        try {
            spotlightRadius = 0f

            spotlightAnimator = ValueAnimator.ofFloat(0f, maxSpotlightRadius).apply {
                duration = 600
                interpolator = OvershootInterpolator(0.6f)
                addUpdateListener { animator ->
                    spotlightRadius = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } catch (e: Exception) {
            spotlightRadius = maxSpotlightRadius
            invalidate()
        }
    }

    private fun cleanupAnimations() {
        try {
            spotlightAnimator?.cancel()
            pulseAnimator?.cancel()
            breathingAnimator?.cancel()
            maskBitmap?.recycle()
            maskBitmap = null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur cleanup animations: ${e.message}")
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupAnimations()
    }
}
