package com.dedoware.shoopt.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utilitaire pour gérer les informations relatives à la première utilisation de l'application
 * et coordonner l'affichage de l'onboarding pour les nouveaux utilisateurs.
 */
class FirstTimeUserManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "first_time_user_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    /**
     * Vérifie s'il s'agit du premier lancement de l'application
     */
    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirst) {
            // Marque que l'app a été lancée au moins une fois
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }

        return isFirst
    }

    /**
     * Vérifie si l'onboarding a été complété
     */
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Marque l'onboarding comme terminé
     */
    fun markOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    /**
     * Réinitialise l'onboarding
     * Utile pour les tests ou pour permettre à l'utilisateur de revoir l'onboarding
     */
    fun resetOnboarding() {
        prefs.edit()
            .putBoolean(KEY_FIRST_LAUNCH, true)
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .apply()
    }
}
