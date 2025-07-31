package com.dedoware.shoopt.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
        private const val TAG = "UnifiedGuideSystem"

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
            Log.d(TAG, "=== DEBUT UnifiedGuideSystem.showGuide ===")
            Log.d(TAG, "Guide ID: ${guide.id}, ForceShow: $forceShow")
            Log.d(TAG, "TargetView: ${guide.targetView}, SpotlightType: ${guide.spotlightType}")

            // Vérifier si déjà montré (sauf si forceShow)
            if (!forceShow && isGuideShown(context, guide.id)) {
                Log.d(TAG, "Guide déjà montré - abandon")
                return
            }

            // Fermer le guide actuel si il existe
            dismissCurrentGuide()
            Log.d(TAG, "Guide actuel fermé")

            // Créer et afficher le nouveau guide
            Log.d(TAG, "Création de l'overlay...")
            val overlay = GuideOverlay(context, guide) {
                Log.d(TAG, "Callback onDismiss appelé")
                dismissCurrentGuide()
                if (!forceShow) {
                    setGuideShown(context, guide.id, true)
                }
                guide.onComplete?.invoke()
            }

            Log.d(TAG, "Ajout de l'overlay au parentView...")
            parentView.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            currentOverlay = overlay
            isGuideActive = true

            Log.d(TAG, "Démarrage de l'animation show()...")
            // Afficher avec animation
            overlay.show()

            Log.d(TAG, "Guide affiché avec succès: ${guide.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur affichage guide: ${e.message}")
            e.printStackTrace()
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
        Log.d(TAG, "Tous les guides réinitialisés")
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

                val confirmButton = tooltip.findViewById<View>(R.id.tooltip_confirm_button)
                val confirmText = tooltip.findViewById<TextView>(R.id.tooltip_confirm_text)
                val skipButton = tooltip.findViewById<LinearLayout>(R.id.tooltip_skip_button)

                if (guide.showConfirmButton) {
                    confirmText?.text = guide.confirmText
                    confirmButton?.setOnClickListener { onDismiss() }
                    confirmButton?.visibility = View.VISIBLE
                } else {
                    confirmButton?.visibility = View.GONE
                }

                skipButton?.setOnClickListener { onDismiss() }
            }

            addView(tooltipView)
            positionTooltip()
        }

        private fun calculateSpotlight() {
            guide.targetView?.let { target ->
                // Attendre que l'overlay soit mesuré avant de calculer
                post {
                    val targetLocation = IntArray(2)
                    val overlayLocation = IntArray(2)

                    target.getLocationOnScreen(targetLocation)
                    this.getLocationOnScreen(overlayLocation)

                    // Ajuster les coordonnées relatives à l'overlay
                    val relativeX = targetLocation[0] - overlayLocation[0]
                    val relativeY = targetLocation[1] - overlayLocation[1]

                    when (guide.spotlightType) {
                        SpotlightType.CIRCLE -> {
                            val centerX = relativeX + target.width / 2f
                            val centerY = relativeY + target.height / 2f
                            val radius = max(target.width, target.height) / 2f + SPOTLIGHT_PADDING

                            spotlightRect.set(
                                centerX - radius,
                                centerY - radius,
                                centerX + radius,
                                centerY + radius
                            )
                        }
                        SpotlightType.ROUNDED_RECT -> {
                            // Correction pour un centrage parfait avec coordonnées relatives
                            val padding = SPOTLIGHT_PADDING
                            spotlightRect.set(
                                relativeX - padding,
                                relativeY - padding,
                                relativeX + target.width + padding,
                                relativeY + target.height + padding
                            )
                        }
                        SpotlightType.NONE -> {
                            spotlightRect.setEmpty()
                        }
                    }

                    // Forcer un redraw après le calcul
                    invalidate()
                }
            }
        }

        private fun positionTooltip() {
            tooltipView?.let { tooltip ->
                post {
                    // Get reliable screen dimensions
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    // Use more reliable method to get safe area
                    val activity = context as? android.app.Activity
                    var statusBarHeight = 0
                    var navigationBarHeight = 0

                    // Get status bar height
                    val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
                    if (statusBarId > 0) {
                        statusBarHeight = resources.getDimensionPixelSize(statusBarId)
                    }

                    // Get navigation bar height
                    val navBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                    if (navBarId > 0) {
                        navigationBarHeight = resources.getDimensionPixelSize(navBarId)
                    }

                    // Fallback values if detection fails
                    if (statusBarHeight == 0) statusBarHeight = (24 * displayMetrics.density).toInt()
                    if (navigationBarHeight == 0) navigationBarHeight = (48 * displayMetrics.density).toInt()

                    // Calculate truly safe area
                    val safeAreaTop = statusBarHeight
                    val safeAreaBottom = screenHeight - navigationBarHeight
                    val safeAreaHeight = safeAreaBottom - safeAreaTop

                    // Tooltip dimensions
                    val maxTooltipWidthDp = 320
                    val minTooltipWidthDp = 280
                    val density = displayMetrics.density
                    var tooltipWidthPx = (maxTooltipWidthDp * density).toInt()

                    val margin = (20 * density).toInt() // Increased margin for better separation
                    val minDistanceFromSpotlight = (32 * density).toInt() // Minimum distance from spotlight

                    // Adjust width if needed
                    if (guide.targetView != null && guide.spotlightType != SpotlightType.NONE) {
                        val overlayLocation = IntArray(2)
                        this.getLocationOnScreen(overlayLocation)

                        val targetLocation = IntArray(2)
                        guide.targetView.getLocationOnScreen(targetLocation)

                        val relativeTargetX = targetLocation[0] - overlayLocation[0]
                        val spotlightPadding = SPOTLIGHT_PADDING.toInt()

                        val spaceLeft = relativeTargetX - spotlightPadding
                        val spaceRight = screenWidth - (relativeTargetX + guide.targetView.width + spotlightPadding)

                        val availableHorizontalSpace = maxOf(spaceLeft, spaceRight) - margin * 2
                        val maxAvailableWidth = minOf(screenWidth - margin * 2, availableHorizontalSpace.coerceAtLeast(0))
                        val minWidthPx = (minTooltipWidthDp * density).toInt()

                        tooltipWidthPx = when {
                            maxAvailableWidth >= tooltipWidthPx -> tooltipWidthPx
                            maxAvailableWidth >= minWidthPx -> maxAvailableWidth
                            else -> minWidthPx
                        }
                    }

                    // Measure tooltip with width constraint
                    val widthSpec = View.MeasureSpec.makeMeasureSpec(tooltipWidthPx, View.MeasureSpec.EXACTLY)
                    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    tooltip.measure(widthSpec, heightSpec)

                    var tooltipHeight = tooltip.measuredHeight
                    val tooltipWidth = tooltip.measuredWidth

                    // CRITICAL: Check if tooltip fits in safe area and constrain if necessary
                    // CORRECTION: Better height management and positioning

                    // Calculate available space for positioning BEFORE constraining height
                    val overlayLocation = IntArray(2)
                    this.getLocationOnScreen(overlayLocation)

                    val targetLocation = IntArray(2)
                    guide.targetView?.getLocationOnScreen(targetLocation)

                    val relativeTargetY = targetLocation[1] - overlayLocation[1]
                    val spotlightPadding = SPOTLIGHT_PADDING.toInt()
                    val actualSpotlightBottom = if (guide.targetView != null) {
                        relativeTargetY + guide.targetView.height + spotlightPadding
                    } else {
                        spotlightPadding
                    }

                    val availableSpaceBelow = (screenHeight - overlayLocation[1]) - actualSpotlightBottom - navigationBarHeight - margin - minDistanceFromSpotlight
                    // Ensure all values are integers to avoid ambiguity
                    val maxReasonableHeight = when {
                        availableSpaceBelow >= 400 -> minOf((350 * density).toInt(), availableSpaceBelow)
                        availableSpaceBelow >= 250 -> minOf((250 * density).toInt(), availableSpaceBelow)
                        else -> minOf((200 * density).toInt(), availableSpaceBelow.coerceAtLeast(150))
                    }

                    Log.d(TAG, "Available space below: ${availableSpaceBelow}px, Natural tooltip height: ${tooltipHeight}px, max allowed: ${maxReasonableHeight}px")

                    if (tooltipHeight > maxReasonableHeight) {
                        Log.d(TAG, "Constraining tooltip from ${tooltipHeight}px to ${maxReasonableHeight}px")

                        // Optimize layout for constrained space
                        optimizeTooltipLayout(tooltip, maxReasonableHeight < 300)

                        // Force exact height constraint
                        val constrainedHeightSpec = View.MeasureSpec.makeMeasureSpec(maxReasonableHeight, View.MeasureSpec.EXACTLY)
                        tooltip.measure(widthSpec, constrainedHeightSpec)
                        tooltipHeight = maxReasonableHeight
                        Log.d(TAG, "Final constrained height: ${tooltipHeight}px")
                    }

                    val params = FrameLayout.LayoutParams(tooltipWidth, tooltipHeight)

                    if (guide.targetView != null && guide.spotlightType != SpotlightType.NONE) {
                        val overlayLocation = IntArray(2)
                        this.getLocationOnScreen(overlayLocation)

                        val targetLocation = IntArray(2)
                        guide.targetView.getLocationOnScreen(targetLocation)

                        val relativeTargetX = targetLocation[0] - overlayLocation[0]
                        val relativeTargetY = targetLocation[1] - overlayLocation[1]

                        val spotlightPadding = SPOTLIGHT_PADDING.toInt()

                        val spotlightLeft = relativeTargetX - spotlightPadding
                        val spotlightRight = relativeTargetX + (guide.targetView?.width ?: 0) + spotlightPadding
                        val spotlightTop = relativeTargetY - spotlightPadding
                        val spotlightBottom = relativeTargetY + (guide.targetView?.height ?: 0) + spotlightPadding

                        // Calculate available spaces within safe area with minimum distance requirement
                        // CORRECTION: Fix spotlight calculations and tooltip height constraints
                        val actualSpotlightTop = relativeTargetY - spotlightPadding
                        val actualSpotlightBottom = relativeTargetY + (guide.targetView?.height ?: 0) + spotlightPadding

                        val spaceLeft = spotlightLeft - margin - minDistanceFromSpotlight
                        val spaceRight = screenWidth - spotlightRight - margin - minDistanceFromSpotlight
                        val spaceAbove = actualSpotlightTop - margin - minDistanceFromSpotlight
                        val spaceBelow = (screenHeight - overlayLocation[1]) - actualSpotlightBottom - navigationBarHeight - margin - minDistanceFromSpotlight

                        Log.d(TAG, "Target bounds: ${guide.targetView.width}x${guide.targetView.height} at (${targetLocation[0]}, ${targetLocation[1]})")
                        Log.d(TAG, "Tooltip dimensions: ${tooltipWidth}x${tooltipHeight}")
                        Log.d(TAG, "Calculs d'espace - Above: $spaceAbove, Below: $spaceBelow, Left: $spaceLeft, Right: $spaceRight")
                        Log.d(TAG, "Spotlight position - Top: $actualSpotlightTop, Bottom: $actualSpotlightBottom")
                        Log.d(TAG, "Screen height: $screenHeight, Overlay Y: ${overlayLocation[1]}, NavBar: $navigationBarHeight")

                        // Enhanced positioning logic with better spacing
                        when {
                            // Priority 1: Below if adequate space with proper distance
                            spaceBelow >= tooltipHeight && spaceBelow >= 100 -> {
                                params.topMargin = actualSpotlightBottom + minDistanceFromSpotlight
                                Log.d(TAG, "Positioning BELOW with topMargin: ${params.topMargin}")

                                // Ensure tooltip doesn't exceed safe area
                                val tooltipBottom = params.topMargin + tooltipHeight + overlayLocation[1]
                                val screenLimit = screenHeight - navigationBarHeight
                                if (tooltipBottom > screenLimit) {
                                    params.topMargin = screenLimit - tooltipHeight - overlayLocation[1] - margin
                                    Log.d(TAG, "Adjusted topMargin to prevent overflow: ${params.topMargin}")
                                }

                                // Center horizontally on target
                                val targetCenterX = relativeTargetX + guide.targetView.width / 2
                                val idealLeftMargin = targetCenterX - tooltipWidth / 2
                                params.leftMargin = idealLeftMargin.coerceIn(margin, screenWidth - tooltipWidth - margin)
                            }
                            // Priority 2: Above if adequate space
                            spaceAbove >= tooltipHeight && spaceAbove >= 100 -> {
                                params.topMargin = (actualSpotlightTop - tooltipHeight - minDistanceFromSpotlight).coerceAtLeast(margin)
                                Log.d(TAG, "Positioning ABOVE with topMargin: ${params.topMargin}")

                                val targetCenterX = relativeTargetX + guide.targetView.width / 2
                                val idealLeftMargin = targetCenterX - tooltipWidth / 2
                                params.leftMargin = idealLeftMargin.coerceIn(margin, screenWidth - tooltipWidth - margin)
                            }
                            // Priority 3: Right side if enough space
                            spaceRight >= tooltipWidth && spaceRight >= 150 -> {
                                params.leftMargin = spotlightRight + minDistanceFromSpotlight
                                Log.d(TAG, "Positioning RIGHT with leftMargin: ${params.leftMargin}")

                                val targetCenterY = relativeTargetY + guide.targetView.height / 2
                                val idealTopMargin = targetCenterY - tooltipHeight / 2
                                params.topMargin = idealTopMargin.coerceIn(margin, safeAreaHeight - tooltipHeight - margin)
                            }
                            // Priority 4: Left side if enough space
                            spaceLeft >= tooltipWidth && spaceLeft >= 150 -> {
                                params.leftMargin = (spotlightLeft - tooltipWidth - minDistanceFromSpotlight).coerceAtLeast(margin)
                                Log.d(TAG, "Positioning LEFT with leftMargin: ${params.leftMargin}")

                                val targetCenterY = relativeTargetY + guide.targetView.height / 2
                                val idealTopMargin = targetCenterY - tooltipHeight / 2
                                params.topMargin = idealTopMargin.coerceIn(margin, safeAreaHeight - tooltipHeight - margin)
                            }
                            // Priority 5: Below even with less space (but maintain minimum distance)
                            spaceBelow > spaceAbove && spaceBelow >= 50 -> {
                                params.topMargin = actualSpotlightBottom + minDistanceFromSpotlight
                                Log.d(TAG, "Positioning BELOW (forced) with topMargin: ${params.topMargin}")

                                // Force fit within safe area
                                val tooltipBottom = params.topMargin + tooltipHeight + overlayLocation[1]
                                val screenLimit = screenHeight - navigationBarHeight
                                if (tooltipBottom > screenLimit) {
                                    params.topMargin = screenLimit - tooltipHeight - overlayLocation[1] - margin
                                    Log.d(TAG, "Force adjusted topMargin: ${params.topMargin}")
                                }

                                // Position horizontally away from center if needed
                                val targetCenterX = relativeTargetX + guide.targetView.width / 2
                                if (tooltipWidth <= screenWidth - margin * 2) {
                                    val idealLeftMargin = targetCenterX - tooltipWidth / 2
                                    params.leftMargin = idealLeftMargin.coerceIn(margin, screenWidth - tooltipWidth - margin)
                                } else {
                                    params.leftMargin = margin
                                }
                            }
                            // Priority 6: Above even with less space
                            spaceAbove >= 50 -> {
                                params.topMargin = (actualSpotlightTop - tooltipHeight - minDistanceFromSpotlight).coerceAtLeast(margin)
                                Log.d(TAG, "Positioning ABOVE (forced) with topMargin: ${params.topMargin}")

                                val targetCenterX = relativeTargetX + guide.targetView.width / 2
                                val idealLeftMargin = targetCenterX - tooltipWidth / 2
                                params.leftMargin = idealLeftMargin.coerceIn(margin, screenWidth - tooltipWidth - margin)
                            }
                            // Fallback: Position in top-left corner with safe distance
                            else -> {
                                Log.d(TAG, "Using emergency fallback positioning")
                                params.leftMargin = margin
                                params.topMargin = safeAreaTop + margin

                                // Ensure it doesn't overlap with spotlight
                                if (params.topMargin + tooltipHeight > spotlightTop - minDistanceFromSpotlight) {
                                    params.topMargin = safeAreaTop + margin
                                    params.leftMargin = if (relativeTargetX > screenWidth / 2) {
                                        margin // Position left if target is on right
                                    } else {
                                        screenWidth - tooltipWidth - margin // Position right if target is on left
                                    }
                                }
                            }
                        }

                        // Final safety check - NEVER exceed safe area or overlap spotlight
                        params.topMargin = params.topMargin.coerceIn(safeAreaTop + margin, safeAreaBottom - tooltipHeight - margin)
                        params.leftMargin = params.leftMargin.coerceIn(margin, screenWidth - tooltipWidth - margin)

                        params.gravity = Gravity.TOP or Gravity.START
                    } else {
                        // Center on screen within safe area
                        params.gravity = Gravity.CENTER
                        params.topMargin = (safeAreaHeight - tooltipHeight) / 2 + safeAreaTop
                    }

                    Log.d(TAG, "Tooltip positioned: top=${params.topMargin}, left=${params.leftMargin}, height=$tooltipHeight, safeBottom=$safeAreaBottom")

                    tooltip.layoutParams = params
                    tooltip.visibility = View.VISIBLE
                    tooltip.alpha = 1f
                    tooltip.requestLayout()
                }
            }
        }

        private fun optimizeTooltipLayout(tooltip: View, isVeryConstrained: Boolean) {
            Log.d(TAG, "Optimizing tooltip layout, very constrained: $isVeryConstrained")

            // Optimize title
            val titleView = tooltip.findViewById<TextView>(R.id.tooltip_title)
            titleView?.let { title ->
                if (isVeryConstrained) {
                    title.textSize = 16f // Reduced from 18sp
                    title.maxLines = 1
                    title.ellipsize = android.text.TextUtils.TruncateAt.END
                } else {
                    title.textSize = 17f // Slightly reduced
                    title.maxLines = 2
                }

                // Reduce margins
                val params = title.layoutParams as? ViewGroup.MarginLayoutParams
                params?.bottomMargin = (8 * resources.displayMetrics.density).toInt()
                title.layoutParams = params
            }

            // Optimize description
            val descriptionView = tooltip.findViewById<TextView>(R.id.tooltip_description)
            descriptionView?.let { desc ->
                if (isVeryConstrained) {
                    desc.maxLines = 1
                    desc.textSize = 13f // Reduced from 15sp
                } else {
                    desc.maxLines = 2
                    desc.textSize = 14f // Slightly reduced
                }
                desc.ellipsize = android.text.TextUtils.TruncateAt.END
                desc.setSingleLine(isVeryConstrained)

                // Reduce margins
                val params = desc.layoutParams as? ViewGroup.MarginLayoutParams
                params?.bottomMargin = (12 * resources.displayMetrics.density).toInt()
                desc.layoutParams = params
            }

            // Optimize confirm button
            val confirmButton = tooltip.findViewById<View>(R.id.tooltip_confirm_button)
            confirmButton?.let { button ->
                val params = button.layoutParams
                if (isVeryConstrained) {
                    params.height = (40 * resources.displayMetrics.density).toInt() // Reduced from 50dp
                } else {
                    params.height = (45 * resources.displayMetrics.density).toInt() // Slightly reduced
                }
                button.layoutParams = params

                // Reduce margins
                val marginParams = params as? ViewGroup.MarginLayoutParams
                marginParams?.bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }

            // Optimize confirm button text
            val confirmText = tooltip.findViewById<TextView>(R.id.tooltip_confirm_text)
            confirmText?.let { text ->
                text.textSize = if (isVeryConstrained) 14f else 15f // Reduced from 16sp
            }

            // Optimize skip button
            val skipButton = tooltip.findViewById<LinearLayout>(R.id.tooltip_skip_button)
            skipButton?.let { skip ->
                val params = skip.layoutParams as? ViewGroup.MarginLayoutParams
                params?.topMargin = 0 // Remove top margin
                skip.layoutParams = params

                // Optimize skip button text
                val skipText = skip.findViewById<TextView>(android.R.id.text1)
                    ?: skip.getChildAt(1) as? TextView // Get the text view in skip button
                skipText?.textSize = if (isVeryConstrained) 12f else 13f // Reduced from 14sp
            }

            // Optimize icon if very constrained
            if (isVeryConstrained) {
                val iconCard = tooltip.findViewById<View>(R.id.tooltip_icon_card)
                iconCard?.let { icon ->
                    val params = icon.layoutParams
                    params.width = (32 * resources.displayMetrics.density).toInt() // Reduced from 40dp
                    params.height = (32 * resources.displayMetrics.density).toInt()
                    icon.layoutParams = params

                    // Reduce margin
                    val marginParams = params as? ViewGroup.MarginLayoutParams
                    marginParams?.bottomMargin = (8 * resources.displayMetrics.density).toInt()
                }
            }

            Log.d(TAG, "Layout optimization completed")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (guide.spotlightType != SpotlightType.NONE && !spotlightRect.isEmpty) {
                // Sauvegarder l'état du canvas
                val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

                // Dessiner l'overlay sombre
                paint.color = Color.parseColor("#CC000000") // Plus opaque pour un meilleur contraste
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                // Configurer la peinture pour le spotlight
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                paint.isAntiAlias = true

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
                        canvas.drawRoundRect(spotlightRect, 30f, 30f, paint)
                    }
                    else -> {}
                }

                // Restaurer l'état du canvas
                paint.xfermode = null
                canvas.restoreToCount(saveCount)

                // Ajouter un contour subtil autour du spotlight pour plus de clarté
                paint.color = Color.parseColor("#40FFFFFF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f

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
                        canvas.drawRoundRect(spotlightRect, 30f, 30f, paint)
                    }
                    else -> {}
                }
            } else {
                // Overlay simple pour les guides centrés
                paint.color = Color.parseColor("#80000000")
                paint.style = Paint.Style.FILL
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
