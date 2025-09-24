package com.dedoware.shoopt.analytics

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestionnaire de consentement RGPD pour l'analytics.
 * Cette classe gère la demande et le stockage du consentement utilisateur
 * pour la collecte de données d'usage conformément au RGPD.
 */
class AnalyticsConsentManager(private val context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        CONSENT_PREFERENCES, Context.MODE_PRIVATE
    )

    companion object {
        private const val CONSENT_PREFERENCES = "analytics_consent_prefs"
        private const val KEY_CONSENT_GIVEN = "consent_given"
        private const val KEY_CONSENT_REQUESTED = "consent_requested"
    }

    /**
     * Vérifie si le consentement a déjà été demandé à l'utilisateur
     */
    fun isConsentRequested(): Boolean {
        return preferences.getBoolean(KEY_CONSENT_REQUESTED, false)
    }

    /**
     * Vérifie si l'utilisateur a donné son consentement pour la collecte de données
     */
    fun isConsentGiven(): Boolean {
        return preferences.getBoolean(KEY_CONSENT_GIVEN, false)
    }

    /**
     * Enregistre la décision de l'utilisateur concernant le consentement
     */
    fun setConsent(granted: Boolean) {
        preferences.edit()
            .putBoolean(KEY_CONSENT_GIVEN, granted)
            .putBoolean(KEY_CONSENT_REQUESTED, true)
            .apply()

        // Mettre à jour le service d'analytics
        if (granted) {
            AnalyticsService.getInstance(context).enableTracking()
        } else {
            AnalyticsService.getInstance(context).disableTracking()
        }
    }

    /**
     * Marque le consentement comme ayant été demandé, même si l'utilisateur n'a pas encore répondu
     */
    fun markConsentAsRequested() {
        preferences.edit()
            .putBoolean(KEY_CONSENT_REQUESTED, true)
            .apply()
    }

    /**
     * Réinitialise le statut du consentement (utilisé pour les tests ou en cas de mise à jour majeure)
     */
    fun resetConsentStatus() {
        preferences.edit()
            .putBoolean(KEY_CONSENT_REQUESTED, false)
            .putBoolean(KEY_CONSENT_GIVEN, false)
            .apply()
    }
}
