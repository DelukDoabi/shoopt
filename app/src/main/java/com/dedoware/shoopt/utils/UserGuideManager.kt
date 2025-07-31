package com.dedoware.shoopt.utils

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.doOnPreDraw
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ui.components.SpotlightView

/**
 * Gestionnaire de guides utilisateur moderne et réutilisable.
 * Permet de créer des séquences de tooltips et spotlights pour guider l'utilisateur
 * à travers les fonctionnalités de l'application.
 */
class UserGuideManager(private val activity: Activity) {

    private var currentStep = 0
    private var steps = mutableListOf<GuideStep>()
    private var onCompleteCallback: (() -> Unit)? = null
    private var rootView: ViewGroup? = null
    private var currentSpotlight: SpotlightView? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        // Clés de préférences pour suivre les guides complétés
        const val PREF_USER_GUIDES = "pref_user_guides"

        // Identifiants des guides disponibles
        const val GUIDE_MAIN_SCREEN = "guide_main_screen"
        const val GUIDE_PRODUCT_CHOICE = "guide_product_choice"
        const val GUIDE_BARCODE_SCAN = "guide_barcode_scan"
        const val GUIDE_MANUAL_ENTRY = "guide_manual_entry"
        const val GUIDE_ADD_FIRST_PRODUCT = "guide_add_first_product"
    }

    /**
     * Initialise un nouveau guide avec l'identifiant spécifié.
     * @param guideId identifiant unique du guide pour le suivi des guides complétés
     * @param forceShow si true, affiche le guide même s'il a déjà été vu
     */
    fun initGuide(guideId: String, forceShow: Boolean = false): UserGuideManager {
        steps.clear()
        currentStep = 0

        // Vérifier si le guide a déjà été affiché
        if (!forceShow) {
            val preferences = activity.getSharedPreferences(PREF_USER_GUIDES, Activity.MODE_PRIVATE)
            val guideShown = preferences.getBoolean(guideId, false)

            if (guideShown) {
                // Ne pas afficher le guide si l'utilisateur l'a déjà vu
                return this
            }

            // Mémoriser que ce guide va être affiché
            preferences.edit().putBoolean(guideId, true).apply()
        }

        return this
    }

    /**
     * Ajoute une étape au guide avec un spotlight sur une vue.
     */
    fun addStep(
        targetView: View,
        @StringRes titleResId: Int,
        @StringRes descriptionResId: Int,
        @DrawableRes iconResId: Int? = null,
        spotlightShape: SpotlightView.Shape = SpotlightView.Shape.CIRCLE,
        paddingDp: Int = 8,
        position: SpotlightView.TooltipPosition = SpotlightView.TooltipPosition.AUTO
    ): UserGuideManager {
        steps.add(
            GuideStep(
                targetView = targetView,
                titleResId = titleResId,
                descriptionResId = descriptionResId,
                iconResId = iconResId,
                spotlightShape = spotlightShape,
                paddingDp = paddingDp,
                position = position
            )
        )
        return this
    }

    /**
     * Définit une action à exécuter lorsque le guide est terminé.
     */
    fun setOnCompleteListener(callback: () -> Unit): UserGuideManager {
        onCompleteCallback = callback
        return this
    }

    /**
     * Démarre le guide utilisateur.
     */
    fun start() {
        if (steps.isEmpty()) return

        // Trouver la racine de l'activité pour y ajouter le spotlight
        rootView = activity.window.decorView.findViewById(android.R.id.content)

        // S'assurer que le layout est complètement chargé avant d'afficher le guide
        rootView?.doOnPreDraw {
            showCurrentStep()
        }
    }

    /**
     * Affiche l'étape actuelle du guide.
     */
    private fun showCurrentStep() {
        if (currentStep >= steps.size) {
            // Guide terminé
            onCompleteCallback?.invoke()
            return
        }

        val step = steps[currentStep]
        val isLastStep = currentStep == steps.size - 1

        // Créer une nouvelle vue de spotlight
        currentSpotlight = SpotlightView(activity).apply {
            // Configurer l'apparence
            setSpotlightColor(Color.parseColor("#80000000"))

            // Définir le contenu du tooltip
            setTooltipContent(
                titleResId = step.titleResId,
                descriptionResId = step.descriptionResId,
                iconResId = step.iconResId,
                isLastStep = isLastStep
            )

            // Configurer les actions des boutons
            setCallbacks(
                onSkip = {
                    // Ignorer le reste du guide
                    hideCurrentSpotlight {
                        onCompleteCallback?.invoke()
                    }
                },
                onNext = {
                    // Passer à l'étape suivante
                    hideCurrentSpotlight {
                        currentStep++
                        showCurrentStep()
                    }
                }
            )

            // Définir la cible du spotlight
            setTarget(
                targetView = step.targetView,
                shape = step.spotlightShape,
                paddingDp = step.paddingDp
            )
        }

        // Ajouter le spotlight à la vue racine
        rootView?.addView(currentSpotlight)
    }

    /**
     * Cache le spotlight actuel avec une animation et exécute une action ensuite.
     */
    private fun hideCurrentSpotlight(onHidden: () -> Unit) {
        currentSpotlight?.animateOut {
            currentSpotlight?.let { spotlight ->
                rootView?.removeView(spotlight)
                currentSpotlight = null
                onHidden()
            }
        } ?: onHidden()
    }

    /**
     * Arrête immédiatement le guide utilisateur.
     */
    fun stop() {
        hideCurrentSpotlight {}
    }

    /**
     * Classe représentant une étape du guide utilisateur.
     */
    data class GuideStep(
        val targetView: View,
        @StringRes val titleResId: Int,
        @StringRes val descriptionResId: Int,
        @DrawableRes val iconResId: Int? = null,
        val spotlightShape: SpotlightView.Shape = SpotlightView.Shape.CIRCLE,
        val paddingDp: Int = 8,
        val position: SpotlightView.TooltipPosition = SpotlightView.TooltipPosition.AUTO
    )

    /**
     * Marque un guide comme non vu pour qu'il puisse être affiché à nouveau.
     */
    fun resetGuideStatus(guideId: String) {
        activity.getSharedPreferences(PREF_USER_GUIDES, Activity.MODE_PRIVATE)
            .edit()
            .putBoolean(guideId, false)
            .apply()
    }

    /**
     * Vérifie si un guide a déjà été montré à l'utilisateur.
     */
    fun hasGuideBeenShown(guideId: String): Boolean {
        return activity.getSharedPreferences(PREF_USER_GUIDES, Activity.MODE_PRIVATE)
            .getBoolean(guideId, false)
    }

    /**
     * Réinitialise tous les guides pour qu'ils soient affichés à nouveau.
     */
    fun resetAllGuides() {
        activity.getSharedPreferences(PREF_USER_GUIDES, Activity.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
