package com.dedoware.shoopt.utils

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import com.dedoware.shoopt.R
import com.dedoware.shoopt.models.SpotlightItem
import com.dedoware.shoopt.ui.SpotlightContentView
import com.dedoware.shoopt.ui.SpotlightView

/**
 * Gestionnaire moderne du système de spotlight pour guider les utilisateurs
 * Intégré dans l'expérience d'onboarding globale
 */
class SpotlightManager private constructor(private val activity: Activity) {

    private var spotlightItems = mutableListOf<SpotlightItem>()
    private var currentIndex = 0
    private var isActive = false
    private var onCompleteListener: (() -> Unit)? = null

    private var spotlightView: SpotlightView? = null
    private var contentView: SpotlightContentView? = null

    companion object {
        private val instances = mutableMapOf<String, SpotlightManager>()

        /**
         * Obtient une instance du SpotlightManager pour une activité donnée
         */
        fun getInstance(activity: Activity): SpotlightManager {
            val key = activity.javaClass.simpleName
            return instances.getOrPut(key) { SpotlightManager(activity) }
        }

        /**
         * Vérifie si le spotlight doit être affiché pour un écran donné
         */
        fun shouldShowSpotlight(activity: Activity, screenKey: String): Boolean {
            return UserPreferences.shouldShowSpotlight(activity, screenKey)
        }
    }

    /**
     * Configure les éléments à mettre en surbrillance
     */
    fun setSpotlightItems(items: List<SpotlightItem>): SpotlightManager {
        spotlightItems.clear()
        spotlightItems.addAll(items)
        currentIndex = 0
        return this
    }

    /**
     * Définit le callback à exécuter lorsque le tour de spotlight est terminé
     */
    fun setOnCompleteListener(listener: () -> Unit): SpotlightManager {
        this.onCompleteListener = listener
        return this
    }

    /**
     * Démarre le tour de spotlight
     */
    fun start(screenKey: String) {
        if (isActive || spotlightItems.isEmpty()) return

        // Vérifier si on doit afficher le spotlight pour cet écran
        if (!shouldShowSpotlight(activity, screenKey)) {
            onCompleteListener?.invoke()
            return
        }

        isActive = true
        showCurrentSpotlight()

        // Analytics
        AnalyticsManager.logUserAction(
            "spotlight_started",
            "onboarding",
            mapOf(
                "screen" to screenKey,
                "total_items" to spotlightItems.size.toString()
            )
        )
    }

    private fun showCurrentSpotlight() {
        if (currentIndex >= spotlightItems.size) {
            complete()
            return
        }

        val currentItem = spotlightItems[currentIndex]

        // Attendre que la vue target soit layoutée
        currentItem.targetView.doOnLayout {
            createSpotlightOverlay(currentItem)
        }
    }

    private fun createSpotlightOverlay(item: SpotlightItem) {
        // Nettoyer les vues existantes
        cleanupViews()

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        // Créer la vue spotlight
        spotlightView = SpotlightView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Créer la vue de contenu
        contentView = SpotlightContentView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // Positionner le contenu intelligemment
                val targetLocation = IntArray(2)
                item.targetView.getLocationOnScreen(targetLocation)
                val screenHeight = activity.resources.displayMetrics.heightPixels

                if (targetLocation[1] < screenHeight / 2) {
                    // Target en haut, contenu en bas
                    gravity = android.view.Gravity.BOTTOM
                    bottomMargin = activity.resources.getDimensionPixelSize(R.dimen.spotlight_content_margin)
                } else {
                    // Target en bas, contenu en haut
                    gravity = android.view.Gravity.TOP
                    topMargin = activity.resources.getDimensionPixelSize(R.dimen.spotlight_content_margin)
                }

                marginStart = activity.resources.getDimensionPixelSize(R.dimen.spotlight_content_margin)
                marginEnd = activity.resources.getDimensionPixelSize(R.dimen.spotlight_content_margin)
            }

            setContent(
                title = activity.getString(item.titleRes),
                description = activity.getString(item.descriptionRes),
                showNextButton = currentIndex < spotlightItems.size - 1,
                showSkipButton = true,
                nextButtonText = if (currentIndex == spotlightItems.size - 1)
                    activity.getString(R.string.spotlight_finish)
                else
                    activity.getString(R.string.spotlight_next)
            )

            setOnNextClickListener {
                nextSpotlight()
            }

            setOnSkipClickListener {
                skipSpotlight()
            }
        }

        // Ajouter les vues au root
        rootView.addView(spotlightView)
        rootView.addView(contentView)

        // Afficher le spotlight
        spotlightView?.showSpotlight(item) {
            // Callback de dismiss du spotlight
        }

        // Animer l'entrée du contenu
        contentView?.animateIn()
    }

    private fun nextSpotlight() {
        AnalyticsManager.logUserAction(
            "spotlight_next",
            "onboarding",
            mapOf("step_index" to currentIndex.toString())
        )

        currentIndex++
        contentView?.animateOut {
            showCurrentSpotlight()
        }
    }

    private fun skipSpotlight() {
        AnalyticsManager.logUserAction(
            "spotlight_skip",
            "onboarding",
            mapOf("step_index" to currentIndex.toString())
        )

        complete()
    }

    private fun complete() {
        if (!isActive) return

        isActive = false

        // Marquer le spotlight comme vu pour cet écran
        val screenKey = activity.javaClass.simpleName
        UserPreferences.markSpotlightSeen(activity, screenKey)

        AnalyticsManager.logUserAction(
            "spotlight_completed",
            "onboarding",
            mapOf(
                "screen" to screenKey,
                "completed_steps" to currentIndex.toString(),
                "total_steps" to spotlightItems.size.toString()
            )
        )

        contentView?.animateOut {
            cleanupViews()
            onCompleteListener?.invoke()
        } ?: run {
            cleanupViews()
            onCompleteListener?.invoke()
        }
    }

    private fun cleanupViews() {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        spotlightView?.let { rootView.removeView(it) }
        contentView?.let { rootView.removeView(it) }

        spotlightView = null
        contentView = null
    }

    /**
     * Force l'arrêt du spotlight
     */
    fun stop() {
        if (isActive) {
            isActive = false
            cleanupViews()
        }
    }

    /**
     * Vérifie si le spotlight est actuellement actif
     */
    fun isSpotlightActive(): Boolean = isActive
}
