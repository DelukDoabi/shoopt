package com.dedoware.shoopt.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.dedoware.shoopt.R
import com.dedoware.shoopt.models.SpotlightItem
import com.dedoware.shoopt.models.SpotlightShape
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Vue overlay moderne pour le système de spotlight avec animations fluides
 */
class SpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var currentSpotlightItem: SpotlightItem? = null
    private var onDismissListener: (() -> Unit)? = null

    private val overlayPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.spotlight_overlay)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private var spotlightRadius = 0f
    private var targetRect = RectF()
    private var isAnimating = false

    // Animation
    private var radiusAnimator: ValueAnimator? = null

    companion object {
        private const val ANIMATION_DURATION = 300L
        private const val SPOTLIGHT_PADDING = 24f
    }

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun showSpotlight(spotlightItem: SpotlightItem, onDismiss: () -> Unit) {
        this.currentSpotlightItem = spotlightItem
        this.onDismissListener = onDismiss

        calculateTargetBounds(spotlightItem)
        animateSpotlightIn()
    }

    private fun calculateTargetBounds(item: SpotlightItem) {
        val location = IntArray(2)
        item.targetView.getLocationOnScreen(location)

        // Convertir les coordonnées de l'écran aux coordonnées de cette vue
        val thisLocation = IntArray(2)
        getLocationOnScreen(thisLocation)

        targetRect.set(
            (location[0] - thisLocation[0]).toFloat(),
            (location[1] - thisLocation[1]).toFloat(),
            (location[0] - thisLocation[0] + item.targetView.width).toFloat(),
            (location[1] - thisLocation[1] + item.targetView.height).toFloat()
        )

        // Calculer le rayon approprié selon la forme
        spotlightRadius = when (item.shape) {
            SpotlightShape.CIRCLE -> {
                val centerX = targetRect.centerX()
                val centerY = targetRect.centerY()
                val maxDistance = max(
                    max(targetRect.width(), targetRect.height()) / 2f,
                    sqrt((targetRect.width() * targetRect.width() + targetRect.height() * targetRect.height()).toDouble()).toFloat() / 2f
                ) + SPOTLIGHT_PADDING
                maxDistance
            }
            SpotlightShape.ROUNDED_RECTANGLE -> {
                max(targetRect.width(), targetRect.height()) / 2f + SPOTLIGHT_PADDING
            }
        }
    }

    private fun animateSpotlightIn() {
        isAnimating = true
        radiusAnimator?.cancel()

        radiusAnimator = ValueAnimator.ofFloat(0f, spotlightRadius).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animator ->
                spotlightRadius = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            start()
        }
    }

    private fun animateSpotlightOut(onComplete: () -> Unit) {
        isAnimating = true
        radiusAnimator?.cancel()

        radiusAnimator = ValueAnimator.ofFloat(spotlightRadius, 0f).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animator ->
                spotlightRadius = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    onComplete()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val item = currentSpotlightItem ?: return

        // Dessiner l'overlay sombre
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Créer le trou de spotlight
        when (item.shape) {
            SpotlightShape.CIRCLE -> {
                canvas.drawCircle(
                    targetRect.centerX(),
                    targetRect.centerY(),
                    spotlightRadius,
                    clearPaint
                )
            }
            SpotlightShape.ROUNDED_RECTANGLE -> {
                val expandedRect = RectF(
                    targetRect.left - SPOTLIGHT_PADDING,
                    targetRect.top - SPOTLIGHT_PADDING,
                    targetRect.right + SPOTLIGHT_PADDING,
                    targetRect.bottom + SPOTLIGHT_PADDING
                )
                canvas.drawRoundRect(
                    expandedRect,
                    spotlightRadius / 4f,
                    spotlightRadius / 4f,
                    clearPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return true

        val item = currentSpotlightItem ?: return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y

                // Vérifier si le touch est sur la target
                val isTargetTouch = targetRect.contains(touchX, touchY)

                if (isTargetTouch && item.dismissOnTargetTouch) {
                    dismiss()
                    return true
                } else if (!isTargetTouch && item.dismissOnTouchOutside) {
                    dismiss()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    fun dismiss() {
        if (isAnimating) return

        animateSpotlightOut {
            onDismissListener?.invoke()
            if (parent is ViewGroup) {
                (parent as ViewGroup).removeView(this)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        radiusAnimator?.cancel()
    }
}
