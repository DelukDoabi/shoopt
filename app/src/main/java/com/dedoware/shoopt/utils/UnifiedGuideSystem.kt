package com.dedoware.shoopt.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.dedoware.shoopt.R
import com.google.android.material.button.MaterialButton
import kotlin.math.*

/**
 * Système de guide unifié moderne - Version simplifiée et robuste
 * Remplace tous les anciens systèmes de guides
 */
class UnifiedGuideSystem private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: UnifiedGuideSystem? = null

        fun getInstance(): UnifiedGuideSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedGuideSystem().also { INSTANCE = it }
            }
        }

        // Constantes
        private const val PREF_NAME = "unified_guide_prefs"
        private const val ANIMATION_DURATION = 300L
        private const val SPOTLIGHT_PADDING = 20f
    }

    private var currentOverlay: GuideOverlay? = null
    private var isGuideActive = false

    // Configuration d'un guide
    data class Guide(
        val id: String,
        val title: String,
        val description: String,
        val targetView: View? = null,
        val spotlightType: SpotlightType = SpotlightType.CIRCLE,
        val showConfirmButton: Boolean = false,
        val confirmText: String = "Compris !",
        val onComplete: (() -> Unit)? = null
    )

    enum class SpotlightType {
        CIRCLE, ROUNDED_RECT, NONE
    }

    /**
     * Affiche un guide avec gestion simplifiée
     */
    fun showGuide(
        context: Context,
        parentView: ViewGroup,
        guide: Guide,
        forceShow: Boolean = false
    ) {
        try {
            // Vérifier si déjà montré (sauf si forceShow)
            if (!forceShow && isGuideShown(context, guide.id)) {
                return
            }

            // Fermer le guide actuel si il existe
            dismissCurrentGuide()

            // Créer et afficher le nouveau guide
            val overlay = GuideOverlay(context, guide) {
                dismissCurrentGuide()
                if (!forceShow) {
                    setGuideShown(context, guide.id, true)
                }
                guide.onComplete?.invoke()
            }

            parentView.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            currentOverlay = overlay
            isGuideActive = true

            // Afficher avec animation
            overlay.show()

            CrashlyticsManager.log("Guide affiché: ${guide.id}")

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur affichage guide: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Force le replay d'un guide
     */
    fun replayGuide(
        context: Context,
        parentView: ViewGroup,
        guide: Guide
    ) {
        showGuide(context, parentView, guide, forceShow = true)
    }

    /**
     * Ferme le guide actuel
     */
    fun dismissCurrentGuide() {
        currentOverlay?.hide {
            (currentOverlay?.parent as? ViewGroup)?.removeView(currentOverlay)
            currentOverlay = null
            isGuideActive = false
        }
    }

    /**
     * Guides prédéfinis pour l'application
     */
    fun getFirstProductGuide(addProductCard: View): Guide {
        return Guide(
            id = "first_product",
            title = "Bienvenue dans Shoopt !",
            description = "Commencez par ajouter votre premier produit en cliquant ici. Vous pourrez scanner son code-barres ou l'ajouter manuellement.",
            targetView = addProductCard,
            spotlightType = SpotlightType.ROUNDED_RECT,
            showConfirmButton = true,
            confirmText = "Commencer !"
        )
    }

    fun getAnalysisGuide(analysisCard: View): Guide {
        return Guide(
            id = "analysis_complete",
            title = "Félicitations ! 🎉",
            description = "Vous avez ajouté votre premier produit ! Maintenant, explorez vos analyses pour découvrir vos habitudes de consommation.",
            targetView = analysisCard,
            spotlightType = SpotlightType.CIRCLE,
            showConfirmButton = true,
            confirmText = "Explorer mes analyses"
        )
    }

    // Gestion des préférences
    private fun isGuideShown(context: Context, guideId: String): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(guideId, false)
    }

    private fun setGuideShown(context: Context, guideId: String, shown: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(guideId, shown)
            .apply()
    }

    /**
     * Réinitialise tous les guides (pour le replay)
     */
    fun resetAllGuides(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        CrashlyticsManager.log("Tous les guides réinitialisés")
    }

    /**
     * Overlay du guide avec animation moderne
     */
    private inner class GuideOverlay(
        context: Context,
        private val guide: Guide,
        private val onDismiss: () -> Unit
    ) : FrameLayout(context) {

        private var spotlightRect = RectF()
        private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var tooltipView: View? = null

        init {
            setBackgroundColor(Color.TRANSPARENT)
            setWillNotDraw(false)
            setupTooltip()
            calculateSpotlight()
        }

        private fun setupTooltip() {
            // Inflater le layout moderne du tooltip
            tooltipView = LayoutInflater.from(context)
                .inflate(R.layout.modern_tooltip_layout, this, false)

            // Configurer le contenu
            tooltipView?.let { tooltip ->
                tooltip.findViewById<TextView>(R.id.tooltip_title)?.text = guide.title
                tooltip.findViewById<TextView>(R.id.tooltip_description)?.text = guide.description

                val confirmButton = tooltip.findViewById<MaterialButton>(R.id.tooltip_confirm_button)
                val skipButton = tooltip.findViewById<MaterialButton>(R.id.tooltip_skip_button)

                if (guide.showConfirmButton) {
                    confirmButton?.text = guide.confirmText
                    confirmButton?.setOnClickListener { onDismiss() }
                    confirmButton?.visibility = View.VISIBLE
                } else {
                    confirmButton?.visibility = View.GONE
                }

                skipButton?.setOnClickListener { onDismiss() }

                // Masquer la mascotte pour une version plus simple
                tooltip.findViewById<View>(R.id.tooltip_mascot)?.visibility = View.GONE
            }

            addView(tooltipView)
            positionTooltip()
        }

        private fun calculateSpotlight() {
            guide.targetView?.let { target ->
                val location = IntArray(2)
                target.getLocationInWindow(location)

                when (guide.spotlightType) {
                    SpotlightType.CIRCLE -> {
                        val centerX = location[0] + target.width / 2f
                        val centerY = location[1] + target.height / 2f
                        val radius = max(target.width, target.height) / 2f + SPOTLIGHT_PADDING

                        spotlightRect.set(
                            centerX - radius,
                            centerY - radius,
                            centerX + radius,
                            centerY + radius
                        )
                    }
                    SpotlightType.ROUNDED_RECT -> {
                        spotlightRect.set(
                            location[0] - SPOTLIGHT_PADDING,
                            location[1] - SPOTLIGHT_PADDING,
                            location[0] + target.width + SPOTLIGHT_PADDING,
                            location[1] + target.height + SPOTLIGHT_PADDING
                        )
                    }
                    SpotlightType.NONE -> {
                        spotlightRect.setEmpty()
                    }
                }
            }
        }

        private fun positionTooltip() {
            tooltipView?.let { tooltip ->
                post {
                    val params = tooltip.layoutParams as LayoutParams

                    if (guide.targetView != null && guide.spotlightType != SpotlightType.NONE) {
                        // Positionner au-dessus ou en dessous de la cible
                        val targetLocation = IntArray(2)
                        guide.targetView.getLocationInWindow(targetLocation)

                        val screenHeight = resources.displayMetrics.heightPixels
                        val tooltipHeight = tooltip.measuredHeight

                        if (targetLocation[1] > screenHeight / 2) {
                            // Positionner au-dessus
                            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            params.topMargin = maxOf(0, targetLocation[1] - tooltipHeight - 100)
                        } else {
                            // Positionner en dessous
                            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            params.topMargin = targetLocation[1] + guide.targetView.height + 50
                        }
                    } else {
                        // Centrer sur l'écran
                        params.gravity = Gravity.CENTER
                    }

                    tooltip.layoutParams = params
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (guide.spotlightType != SpotlightType.NONE && !spotlightRect.isEmpty) {
                // Dessiner l'overlay sombre avec le spotlight
                paint.color = Color.parseColor("#80000000")
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                // Créer le spotlight (zone transparente)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                when (guide.spotlightType) {
                    SpotlightType.CIRCLE -> {
                        val radius = (spotlightRect.right - spotlightRect.left) / 2f
                        canvas.drawCircle(
                            spotlightRect.centerX(),
                            spotlightRect.centerY(),
                            radius,
                            paint
                        )
                    }
                    SpotlightType.ROUNDED_RECT -> {
                        canvas.drawRoundRect(spotlightRect, 20f, 20f, paint)
                    }
                    else -> {}
                }
                paint.xfermode = null
            } else {
                // Overlay simple pour les guides centrés
                paint.color = Color.parseColor("#60000000")
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }

        fun show() {
            alpha = 0f
            tooltipView?.translationY = 100f
            tooltipView?.scaleX = 0.8f
            tooltipView?.scaleY = 0.8f

            animate()
                .alpha(1f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            tooltipView?.animate()
                ?.translationY(0f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(ANIMATION_DURATION)
                ?.setInterpolator(OvershootInterpolator(0.8f))
                ?.start()
        }

        fun hide(onComplete: () -> Unit) {
            animate()
                .alpha(0f)
                .setDuration(ANIMATION_DURATION)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onComplete()
                    }
                })
                .start()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Permettre le clic sur la zone du spotlight
                if (guide.spotlightType != SpotlightType.NONE && spotlightRect.contains(event.x, event.y)) {
                    return false // Laisser l'événement passer à la vue en dessous
                }
                // Fermer en cliquant ailleurs
                onDismiss()
                return true
            }
            return super.onTouchEvent(event)
        }
    }
}
