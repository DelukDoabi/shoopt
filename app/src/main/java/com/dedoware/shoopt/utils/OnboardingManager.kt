package com.dedoware.shoopt.utils

import android.app.Activity
import android.content.Intent
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.activities.OnboardingActivity
import com.dedoware.shoopt.analytics.AnalyticsService

/**
 * Gestionnaire simplifié pour l'expérience d'onboarding globale
 * Coordonne l'introduction et les spotlights de manière simple
 */
object OnboardingManager {

    /**
     * Point d'entrée principal : vérifie et démarre l'onboarding approprié
     */
    fun checkAndStartOnboarding(activity: Activity): Boolean {
        return when {
            !UserPreferences.isOnboardingCompleted(activity) -> {
                startIntroductionOnboarding(activity)
                true
            }
            else -> false
        }
    }

    /**
     * Démarre spécifiquement les spotlights pour un écran donné
     */
    fun startSpotlightsForScreen(activity: Activity, screenKey: String): Boolean {
        return if (UserPreferences.shouldShowSpotlight(activity, screenKey)) {
            // Les spotlights seront gérés par chaque activité via setupSpotlightTour()
            true
        } else {
            false
        }
    }

    /**
     * Démarre l'onboarding d'introduction
     */
    private fun startIntroductionOnboarding(activity: Activity) {
        val intent = Intent(activity, OnboardingActivity::class.java)
        activity.startActivity(intent)

        // Si on n'est pas déjà dans OnboardingActivity, on termine l'activité courante
        if (activity !is OnboardingActivity) {
            activity.finish()
        }
    }

    /**
     * Réinitialise complètement l'onboarding (pour tests/démo)
     */
    fun reset(activity: Activity) {
        UserPreferences.setOnboardingCompleted(activity, false)
        UserPreferences.resetAllSpotlights(activity)

        val params = android.os.Bundle().apply {
            putString("action", "onboarding_reset")
            putString("category", "onboarding")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", params)
    }
}
