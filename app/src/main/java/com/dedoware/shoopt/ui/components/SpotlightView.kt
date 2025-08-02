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
        NONE, CIRCLE, RECTANGLE, ROUNDED_RECTANGLE
    }

    /**
     * Définit les positions possibles du tooltip par rapport à la cible.
     */
    enum class TooltipPosition {
        TOP, BOTTOM, LEFT, RIGHT, AUTO
    }

    /**
     * Configure le spotlight pour cibler une vue spécifique.
     */
    fun setTarget(
        targetView: View,
        shape: Shape = Shape.CIRCLE,
        paddingDp: Int = 8,
        tooltipPos: TooltipPosition = TooltipPosition.AUTO,
        animate: Boolean = true
    ) {
        this.targetView = targetView
        this.spotlightShape = shape
        this.targetPadding = context.resources.displayMetrics.density * paddingDp
        this.tooltipPosition = tooltipPos // Set the desired tooltip position

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
            // targetRect uses absolute window coordinates
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
     * Appelé après que les dimensions de la vue sont connues et que la cible est définie.
     */
    private fun positionTooltip() {
        if (tooltipCard.width == 0 && tooltipCard.height == 0) {
            tooltipCard.post { positionTooltipInternal() }
        } else {
            positionTooltipInternal()
        }
    }

    private fun positionTooltipInternal() {
        val margin = 16f * resources.displayMetrics.density
        val tooltipWidth = tooltipCard.width.toFloat()
        val tooltipHeight = tooltipCard.height.toFloat()
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        if (tooltipWidth == 0f || tooltipHeight == 0f || screenWidth == 0f || screenHeight == 0f) {
            // Not ready to position yet
            return
        }

        var finalX: Float
        var finalY: Float

        // Horizontal centering (common for top/bottom tooltips)
        finalX = targetRect.centerX() - tooltipWidth / 2
        finalX = finalX.coerceIn(margin, screenWidth - tooltipWidth - margin)

        // Vertical bounds for the tooltip's top edge (safe area)
        val safeTopY = systemBarTopInset + margin
        val safeBottomYForTooltipTopEdge = screenHeight - systemBarBottomInset - margin - tooltipHeight

        // If screen is too small for the tooltip to even fit with margins, just place it at the very top.
        if (safeTopY > safeBottomYForTooltipTopEdge) {
            tooltipCard.y = systemBarTopInset.toFloat() // Place at the very top edge of safe area
            tooltipCard.x = finalX
            tooltipCard.requestLayout()
            return
        }

        var preferredY: Float
        var fallbackY: Float

        // Determine preferred and fallback Y positions based on the desired tooltipPosition
        when (this.tooltipPosition) {
            TooltipPosition.TOP -> {
                preferredY = targetRect.top - tooltipHeight - margin
                fallbackY = targetRect.bottom + margin
            }
            TooltipPosition.BOTTOM -> {
                preferredY = targetRect.bottom + margin
                fallbackY = targetRect.top - tooltipHeight - margin
            }
            TooltipPosition.AUTO -> {
                // Simplified AUTO: prefer bottom if target is in top half of screen, else prefer top.
                if (targetRect.centerY() < screenHeight / 2) { // Target in top half
                    preferredY = targetRect.bottom + margin // Prefer tooltip at bottom
                    fallbackY = targetRect.top - tooltipHeight - margin
                } else { // Target in bottom half
                    preferredY = targetRect.top - tooltipHeight - margin // Prefer tooltip at top
                    fallbackY = targetRect.bottom + margin
                }
            }
            // LEFT and RIGHT are not fully handled yet, default to AUTO-like behavior for now
            TooltipPosition.LEFT, TooltipPosition.RIGHT -> {
                 if (targetRect.centerY() < screenHeight / 2) {
                    preferredY = targetRect.bottom + margin
                    fallbackY = targetRect.top - tooltipHeight - margin
                } else {
                    preferredY = targetRect.top - tooltipHeight - margin
                    fallbackY = targetRect.bottom + margin
                }
            }
        }

        // Attempt to place at preferredY, coercing within safe bounds
        finalY = preferredY.coerceIn(safeTopY, safeBottomYForTooltipTopEdge)

        // Check if the preferred placement (after coercion) is acceptable for TOP/BOTTOM.
        var preferredFailed = false
        if (this.tooltipPosition == TooltipPosition.TOP) {
            // If coerced Y (top of tooltip) is now at or below the target's top edge (considering margin),
            // it means the tooltip couldn't truly be placed "above" the target.
            if (finalY >= targetRect.top - margin) {
                preferredFailed = true
            }
        } else if (this.tooltipPosition == TooltipPosition.BOTTOM) {
            // If coerced Y + tooltipHeight (bottom of tooltip) is now at or above the target's bottom edge (considering margin),
            // it means the tooltip couldn't truly be placed "below" the target.
            // finalY is top of tooltip, so finalY + tooltipHeight is bottom of tooltip.
            // targetRect.bottom is bottom of target.
            if (finalY + tooltipHeight <= targetRect.bottom + margin) {
                preferredFailed = true
            }
        }
        // For AUTO, LEFT, RIGHT, we don't apply this "preferredFailed" logic as strictly;
        // the initial coercion is usually sufficient.

        if (preferredFailed && (this.tooltipPosition == TooltipPosition.TOP || this.tooltipPosition == TooltipPosition.BOTTOM)) {
            // Try the fallback position, also coerced
            finalY = fallbackY.coerceIn(safeTopY, safeBottomYForTooltipTopEdge)
        }

        tooltipCard.x = finalX
        tooltipCard.y = finalY
        tooltipCard.requestLayout()
    }


    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)

        if (spotlightShape == Shape.NONE) {
            // Consume touch if not on the tooltipCard itself.
            // TooltipCard, being a child, will get touch first if event is within its bounds.
            return true
        }

        // If a targetView is set and the touch is within its spotlighted area (targetRect)
        if (targetView != null && targetRect.contains(event.x, event.y)) {
            // Do not consume the event; let it pass through to the targetView.
            return false
        }

        // For any other case (e.g., touch on the dimmed background outside the targetRect), consume the event.
        // TooltipCard will still receive events as it's a child.
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (!::bitmap.isInitialized || bitmap.width != w || bitmap.height != h) {
                 if (::bitmap.isInitialized) bitmap.recycle()
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                canvas = Canvas(bitmap)
            }
        }
        // When size changes (e.g. rotation), targetRect and tooltip might need re-calculation
        if (targetView != null) {
            updateTargetRect() // This will also call positionTooltip
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::bitmap.isInitialized) return

        // Clear previous drawing
        bitmap.eraseColor(Color.TRANSPARENT)

        // Draw the semi-transparent overlay
        this.canvas.drawColor(spotlightColor)

        // Cut out the spotlight shape if a target is set and shape is not NONE
        if (targetView != null && spotlightShape != Shape.NONE && targetRect.width() > 0 && targetRect.height() > 0) {
            when (spotlightShape) {
                Shape.CIRCLE -> {
                    val radius = max(targetRect.width(), targetRect.height()) / 2f // Use float for precision
                    if (radius > 0) {
                        this.canvas.drawCircle(
                            targetRect.centerX(),
                            targetRect.centerY(),
                            radius,
                            transparentPaint
                        )
                    }
                }
                Shape.RECTANGLE -> {
                    this.canvas.drawRect(targetRect, transparentPaint)
                }
                Shape.ROUNDED_RECTANGLE -> {
                    val cornerRadius = 16f * context.resources.displayMetrics.density
                    this.canvas.drawRoundRect(targetRect, cornerRadius, cornerRadius, transparentPaint)
                }
                Shape.NONE -> { /* No cutout, already handled by the check above */ }
            }
        }
        // Draw the bitmap with the overlay and cutout onto the main canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }
}
