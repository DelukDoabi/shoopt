package com.dedoware.shoopt.utils

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Gestionnaire pour Firebase Analytics qui offre des méthodes utilitaires
 * pour enregistrer des événements utilisateur et suivre le comportement dans l'application.
 */
object AnalyticsManager {
    private val analytics: FirebaseAnalytics by lazy {
        Firebase.analytics
    }

    /**
     * Initialise le gestionnaire d'analytics.
     * @param isDebug Indique si l'application est en mode debug, ce qui peut désactiver certaines fonctionnalités
     */
    fun initialize(isDebug: Boolean) {
        // Initialisation spécifique pour le mode debug
        if (isDebug) {
            // En mode debug, on peut vouloir désactiver l'envoi de données
            setAnalyticsCollectionEnabled(false)
        } else {
            // En production, on active par défaut
            setAnalyticsCollectionEnabled(true)
        }
    }

    /**
     * Active ou désactive la collecte des données analytiques.
     * Utile pour respecter les préférences de confidentialité de l'utilisateur.
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    /**
     * Enregistre un ID utilisateur anonymisé pour suivre le comportement entre les sessions.
     * Ne doit PAS contenir de données personnelles identifiables.
     */
    fun setUserId(userId: String) {
        // Assurez-vous d'utiliser un ID anonymisé ou haché pour respecter la vie privée
        analytics.setUserId(userId)
    }

    /**
     * Enregistre un événement de navigation d'écran.
     */
    fun logScreenView(screenName: String, screenClass: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    /**
     * Enregistre un événement d'action utilisateur.
     */
    fun logUserAction(action: String, category: String, additionalParams: Map<String, Any>? = null) {
        val params = Bundle().apply {
            putString("action", action)
            putString("category", category)
            additionalParams?.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        analytics.logEvent("user_action", params)
    }

    /**
     * Enregistre un événement lié à l'inscription ou connexion.
     */
    fun logAuthEvent(method: String, success: Boolean) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
            putBoolean("success", success)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, params)
    }

    /**
     * Enregistre une interaction avec une fonctionnalité de l'application.
     */
    fun logFeatureUsage(featureName: String, action: String) {
        val params = Bundle().apply {
            putString("feature_name", featureName)
            putString("action", action)
        }
        analytics.logEvent("feature_usage", params)
    }

    /**
     * Enregistre un événement de performance.
     */
    fun logPerformanceEvent(eventName: String, durationMs: Long) {
        val params = Bundle().apply {
            putLong("duration_ms", durationMs)
        }
        analytics.logEvent(eventName, params)
    }

    /**
     * Définit une propriété utilisateur.
     * Ne pas utiliser pour des informations personnelles identifiables.
     */
    fun setUserProperty(name: String, value: String) {
        analytics.setUserProperty(name, value)
    }

    /**
     * Enregistre un événement personnalisé.
     */
    fun logCustomEvent(eventName: String, params: Bundle?) {
        analytics.logEvent(eventName, params)
    }

    /**
     * Enregistre une sélection de contenu par l'utilisateur.
     */
    fun logSelectContent(contentType: String, itemId: String, itemName: String? = null) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, contentType)
            putString(FirebaseAnalytics.Param.ITEM_ID, itemId)
            if (itemName != null) {
                putString(FirebaseAnalytics.Param.ITEM_NAME, itemName)
            }
        }
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, params)
    }

    /**
     * Enregistre un événement générique.
     */
    fun logEvent(name: String, params: Bundle?) {
        analytics.logEvent(name, params)
    }

    /**
     * Méthode trackEvent pour la compatibilité avec le système de notifications
     */
    fun trackEvent(eventName: String, parameters: Map<String, String>? = null) {
        val params = Bundle().apply {
            parameters?.forEach { (key, value) ->
                putString(key, value)
            }
        }
        analytics.logEvent(eventName, params)
    }
}
