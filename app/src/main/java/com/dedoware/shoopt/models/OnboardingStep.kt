package com.dedoware.shoopt.models

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Modèle unifié pour toutes les étapes d'onboarding
 */
sealed class OnboardingStep {

    /**
     * Étape d'introduction générale (écrans plein)
     */
    data class IntroductionStep(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int
    ) : OnboardingStep()

    /**
     * Étape de spotlight contextuel
     */
    data class SpotlightStep(
        val targetView: View,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val shape: SpotlightShape = SpotlightShape.CIRCLE,
        val screenKey: String,
        val priority: Int = 0, // Pour ordonner les spotlights
        val dismissOnTouchOutside: Boolean = true,
        val dismissOnTargetTouch: Boolean = true
    ) : OnboardingStep()

    /**
     * Étape de fin d'onboarding
     */
    data class CompletionStep(
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val nextAction: OnboardingAction = OnboardingAction.COMPLETE
    ) : OnboardingStep()
}

/**
 * Actions possibles à la fin d'une étape d'onboarding
 */
enum class OnboardingAction {
    COMPLETE,           // Terminer l'onboarding
    NAVIGATE_TO_MAIN,   // Aller à l'écran principal
    START_SPOTLIGHTS    // Démarrer les spotlights contextuels
}

/**
 * État de progression de l'onboarding
 */
data class OnboardingProgress(
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val completedIntroduction: Boolean = false,
    val availableSpotlights: Map<String, List<OnboardingStep.SpotlightStep>> = emptyMap(),
    val completedSpotlights: Set<String> = emptySet()
) {
    val isIntroductionComplete: Boolean get() = completedIntroduction
    val hasAvailableSpotlights: Boolean get() = availableSpotlights.isNotEmpty()
    val progressPercentage: Float get() = if (totalSteps > 0) currentStepIndex.toFloat() / totalSteps else 0f
}
