package com.dedoware.shoopt.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.dedoware.shoopt.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.max

/**
 * Vue personnalisée qui affiche un spotlight sur une vue cible avec un tooltip explicatif.
 * Prend en charge différentes formes de spotlight et positions de tooltip.
 */
class SpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Propriétés de style
    private var spotlightColor = Color.parseColor("#4D000000") // Noir très léger (30% d'opacité)
    private var animationDuration = 300L

    // Propriétés de la vue cible
    private var targetView: View? = null
    private var targetRect = RectF()
    private var targetPadding = 0f
    private var spotlightShape = Shape.CIRCLE

    // Propriétés de dessin
    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val transparentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Éléments UI du tooltip
    private lateinit var tooltipCard: MaterialCardView
    private lateinit var tooltipTitle: TextView
    private lateinit var tooltipDescription: TextView
    private lateinit var tooltipIcon: ImageView
    private lateinit var skipButton: TextView
    private lateinit var nextButton: MaterialButton

    // Position du tooltip par rapport à la cible
    private var tooltipPosition = TooltipPosition.BOTTOM

    // Callbacks
    private var onSkipClickListener: (() -> Unit)? = null
    private var onNextClickListener: (() -> Unit)? = null

    // System Insets
    private var systemBarTopInset: Int = 0
    private var systemBarBottomInset: Int = 0

    init {
        // Rendre cette vue plein écran et transparente
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)

        // Permettre à cette vue de consommer les événements tactiles
        isClickable = true
        isFocusable = true

        // Inflater le layout du tooltip
        val tooltipView = LayoutInflater.from(context).inflate(R.layout.view_spotlight_tooltip, this, false)

        // Récupérer les références aux vues
        tooltipCard = tooltipView.findViewById(R.id.tooltip_card)
        tooltipTitle = tooltipView.findViewById(R.id.tooltip_title)
        tooltipDescription = tooltipView.findViewById(R.id.tooltip_description)
        tooltipIcon = tooltipView.findViewById(R.id.tooltip_icon)
        skipButton = tooltipView.findViewById(R.id.skip_button)
        nextButton = tooltipView.findViewById(R.id.next_button)

        // Configurer les listeners
        skipButton.setOnClickListener { onSkipClickListener?.invoke() }
        nextButton.setOnClickListener { onNextClickListener?.invoke() }

        // Ajouter le tooltip à cette vue
        addView(tooltipView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarTopInset = systemBars.top
            systemBarBottomInset = systemBars.bottom

            // Reposition tooltip if it's already laid out and insets change
            if (tooltipCard.width > 0 && tooltipCard.height > 0) {
                 positionTooltip()
            }
            insets // Propagate insets
        }
        // Request initial insets application
        ViewCompat.requestApplyInsets(this)
    }

    /**
     * Définit les différentes formes possibles pour le spotlight.
     */
    enum class Shape {
        NONE, // Pas de spotlight, juste la tooltip
        CIRCLE,
        RECTANGLE,
        ROUNDED_RECTANGLE
    }

    /**
     * Définit les positions possibles du tooltip par rapport à la cible.
     */
    enum class TooltipPosition {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
        AUTO // Choisi automatiquement la meilleure position
    }

    /**
     * Configure le spotlight pour cibler une vue spécifique.
     */
    fun setTarget(
        targetView: View,
        shape: Shape = Shape.CIRCLE,
        paddingDp: Int = 8,
        animate: Boolean = true
    ) {
        this.targetView = targetView
        this.spotlightShape = shape
        this.targetPadding = context.resources.displayMetrics.density * paddingDp

        updateTargetRect() // This calls positionTooltip

        if (animate) {
            animateIn()
        } else {
            alpha = 1f // Ensure view is visible if not animating in
            invalidate()
        }
    }

    /**
     * Met à jour la position de la zone cible en fonction de la vue ciblée.
     */
    private fun updateTargetRect() {
        targetView?.let { view ->
            // Obtenir la position globale de la vue cible
            val location = IntArray(2)
            view.getLocationInWindow(location)

            // Créer un rectangle autour de la cible avec le padding
            targetRect.set(
                location[0].toFloat() - targetPadding,
                location[1].toFloat() - targetPadding,
                (location[0] + view.width).toFloat() + targetPadding,
                (location[1] + view.height).toFloat() + targetPadding
            )
            positionTooltip()
        }
    }

    /**
     * Anime l'apparition du spotlight.
     */
    private fun animateIn() {
        alpha = 0f // Start transparent for fade-in
        val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
        valueAnimator.duration = animationDuration
        valueAnimator.interpolator = AccelerateDecelerateInterpolator()
        valueAnimator.addUpdateListener { animator ->
            alpha = animator.animatedValue as Float
            // invalidate() // Alpha change itself should trigger redraw
        }
        valueAnimator.start()
    }

    /**
     * Anime la disparition du spotlight puis exécute une action.
     */
    fun animateOut(onAnimationEnd: () -> Unit) {
        val valueAnimator = ValueAnimator.ofFloat(alpha, 0f) // Animate from current alpha
        valueAnimator.duration = animationDuration
        valueAnimator.interpolator = AccelerateDecelerateInterpolator()
        valueAnimator.addUpdateListener { animator ->
            alpha = animator.animatedValue as Float
            // invalidate()
        }
        valueAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onAnimationEnd()
            }
        })
        valueAnimator.start()
    }

    /**
     * Configure le tooltip avec du texte et une icône optionnelle.
     */
    fun setTooltipContent(
        @StringRes titleResId: Int,
        @StringRes descriptionResId: Int,
        @DrawableRes iconResId: Int? = null,
        isLastStep: Boolean = false
    ) {
        tooltipTitle.setText(titleResId)
        tooltipDescription.setText(descriptionResId)

        if (iconResId != null) {
            tooltipIcon.setImageResource(iconResId)
            tooltipIcon.visibility = View.VISIBLE
        } else {
            tooltipIcon.visibility = View.GONE
        }

        if (isLastStep) {
            nextButton.setText(R.string.got_it)
        } else {
            nextButton.setText(R.string.next)
        }
    }

    /**
     * Configure les callbacks pour les boutons.
     */
    fun setCallbacks(
        onSkip: () -> Unit,
        onNext: () -> Unit
    ) {
        onSkipClickListener = onSkip
        onNextClickListener = onNext
    }

    /**
     * Définit le callback appelé lors du clic sur le bouton "Passer" (skip).
     */
    fun setOnSkipListener(listener: (() -> Unit)?) {
        onSkipClickListener = listener
    }

    /**
     * Change la couleur du fond du spotlight.
     */
    fun setSpotlightColor(@ColorInt color: Int) {
        spotlightColor = color
        invalidate()
    }

    /**
     * Positionne le tooltip par rapport à la cible.
     */
    private fun positionTooltip() {
        // Ensure tooltipCard is measured before trying to position it
        if (tooltipCard.width == 0 && tooltipCard.height == 0) {
            tooltipCard.post { positionTooltipInternal() }
        } else {
            positionTooltipInternal()
        }
    }

    private fun positionTooltipInternal() {
        val tooltipWidth = tooltipCard.width.toFloat()
        val tooltipHeight = tooltipCard.height.toFloat()
        val screenWidth = width.toFloat() // Width of SpotlightView
        val screenHeight = height.toFloat() // Height of SpotlightView

        if (tooltipWidth == 0f || tooltipHeight == 0f || screenWidth == 0f || screenHeight == 0f) {
            // Not ready to position yet
            return
        }

        val spaceAbove = targetRect.top
        val spaceBelow = screenHeight - targetRect.bottom
        val spaceLeft = targetRect.left
        val spaceRight = screenWidth - targetRect.right

        val finalPosition = if (tooltipPosition == TooltipPosition.AUTO) {
            when {
                spaceBelow >= tooltipHeight + (16 * resources.displayMetrics.density) -> TooltipPosition.BOTTOM
                spaceAbove >= tooltipHeight + (16 * resources.displayMetrics.density) -> TooltipPosition.TOP
                spaceRight >= tooltipWidth + (16 * resources.displayMetrics.density) -> TooltipPosition.RIGHT
                spaceLeft >= tooltipWidth + (16 * resources.displayMetrics.density) -> TooltipPosition.LEFT
                else -> TooltipPosition.BOTTOM
            }
        } else {
            tooltipPosition
        }

        val margin = 16f * resources.displayMetrics.density // 16dp margin

        when (finalPosition) {
            TooltipPosition.TOP -> {
                tooltipCard.x = targetRect.centerX() - tooltipWidth / 2
                tooltipCard.y = targetRect.top - tooltipHeight - margin
            }
            TooltipPosition.BOTTOM -> {
                tooltipCard.x = targetRect.centerX() - tooltipWidth / 2
                tooltipCard.y = targetRect.bottom + margin
            }
            TooltipPosition.LEFT -> {
                tooltipCard.x = targetRect.left - tooltipWidth - margin
                tooltipCard.y = targetRect.centerY() - tooltipHeight / 2
            }
            TooltipPosition.RIGHT -> {
                tooltipCard.x = targetRect.right + margin
                tooltipCard.y = targetRect.centerY() - tooltipHeight / 2
            }
            else -> { // Should not happen with AUTO
                tooltipCard.x = targetRect.centerX() - tooltipWidth / 2
                tooltipCard.y = targetRect.bottom + margin
            }
        }

        // S'assurer que le tooltip reste dans les limites de l'écran, en tenant compte des insets
        val minX = margin
        val maxX = screenWidth - tooltipWidth - margin
        val minY = systemBarTopInset + margin
        // screenHeight is the total height of SpotlightView, which fits the window.
        // So, the usable height for the tooltip's bottom edge is screenHeight - systemBarBottomInset.
        // The tooltip's y is its top edge, so maxY for y is (screenHeight - systemBarBottomInset) - tooltipHeight - margin.
        val maxY = screenHeight - tooltipHeight - systemBarBottomInset - margin

        tooltipCard.x = tooltipCard.x.coerceIn(minX, maxX)
        tooltipCard.y = tooltipCard.y.coerceIn(minY, maxY)
    }


    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)

        if (spotlightShape == Shape.NONE) {
            return true
        }

        if (targetView != null && targetRect.contains(event.x, event.y)) {
            return false
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (!::bitmap.isInitialized || bitmap.width != w || bitmap.height != h) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                canvas = Canvas(bitmap)
            }
        }
        // When size changes (e.g. rotation), targetRect and tooltip might need re-calculation
        if (targetView != null) {
            updateTargetRect()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::bitmap.isInitialized) return

        bitmap.eraseColor(Color.TRANSPARENT)
        this.canvas.drawColor(spotlightColor)

        when (spotlightShape) {
            Shape.CIRCLE -> {
                val radius = max(targetRect.width(), targetRect.height()) / 2
                if (radius > 0) { // Avoid drawing circle with 0 or negative radius
                    this.canvas.drawCircle(
                        targetRect.centerX(),
                        targetRect.centerY(),
                        radius,
                        transparentPaint
                    )
                }
            }
            Shape.RECTANGLE -> {
                if (targetRect.width() > 0 && targetRect.height() > 0) {
                    this.canvas.drawRect(targetRect, transparentPaint)
                }
            }
            Shape.ROUNDED_RECTANGLE -> {
                if (targetRect.width() > 0 && targetRect.height() > 0) {
                    val cornerRadius = 16f * context.resources.displayMetrics.density
                    this.canvas.drawRoundRect(targetRect, cornerRadius, cornerRadius, transparentPaint)
                }
            }
            Shape.NONE -> { /* No cutout */ }
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }
}
