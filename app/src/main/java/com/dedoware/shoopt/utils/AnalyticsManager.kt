package com.dedoware.shoopt.utils

import android.content.Context
import android.os.Bundle
import com.dedoware.shoopt.analytics.AnalyticsService
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Façade pour l'analytics. Délègue vers `AnalyticsService` (recommandé) si initialisée
 * via `initialize(context, isDebug)`. Reste rétro-compatible : si la façade n'est pas
 * initialisée avec un context, le comportement legacy (utilisation directe de
 * Firebase.analytics) est conservé.
 */
object AnalyticsManager {
    private val analytics: FirebaseAnalytics by lazy {
        Firebase.analytics
    }

    // Référence optionnelle au service centralisé (doit être initialisée au démarrage de l'app)
    @Volatile
    private var analyticsService: AnalyticsService? = null

    /**
     * Initialise le gestionnaire pour le mode debug (rétrocompatible).
     * Préserve le comportement précédent si aucune initialisation avec Context n'est faite.
     */
    fun initialize(isDebug: Boolean) {
        // Comportement rétrocompatible : active/désactive la collecte globale
        setAnalyticsCollectionEnabled(!isDebug)
    }

    /**
     * Initialisation recommandée : doit être appelée depuis l'Application (ou équivalent)
     * pour centraliser le tracking via `AnalyticsService` et respecter les préférences.
     *
     * Important : ne PAS écraser la préférence utilisateur sur l'initialisation. Si
     * `isDebug` est true on désactive la collecte globalement pour éviter d'envoyer des
     * données depuis les builds de debug.
     */
    fun initialize(context: Context, isDebug: Boolean = false) {
        analyticsService = AnalyticsService.getInstance(context.applicationContext)
        // Si on est en debug, explicitement désactiver la collecte. Sinon laisser
        // AnalyticsService charger la préférence utilisateur (opt-in/opt-out).
        if (isDebug) {
            analyticsService?.setAnalyticsCollectionEnabled(false)
        }
    }

    /**
     * Active ou désactive la collecte des données analytiques.
     * Délègue au service centralisé si disponible, sinon utilise Firebase directement.
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analyticsService?.setAnalyticsCollectionEnabled(enabled) ?: analytics.setAnalyticsCollectionEnabled(enabled)
    }

    /**
     * Enregistre un ID utilisateur anonymisé pour suivre le comportement entre les sessions.
     * Ne doit PAS contenir de données personnelles identifiables.
     */
    fun setUserId(userId: String) {
        analyticsService?.setUserId(userId) ?: analytics.setUserId(userId)
    }

    /**
     * Efface l'ID utilisateur dans Firebase (utile pour logout).
     */
    fun clearUserId() {
        analyticsService?.setUserId(null) ?: analytics.setUserId(null)
    }

    /**
     * Enregistre un événement de navigation d'écran.
     */
    fun logScreenView(screenName: String, screenClass: String) {
        analyticsService?.trackScreenView(screenName, screenClass) ?: run {
            val params = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
        }
    }

    /**
     * Enregistre un événement d'action utilisateur.
     */
    fun logUserAction(action: String, category: String, additionalParams: Map<String, Any>? = null) {
        analyticsService?.let { svc ->
            // Pas d'équivalent métier dans le service, on envoie en tant qu'événement personnalisé
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
            svc.logEvent("user_action", params)
        } ?: run {
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
    }

    /**
     * Enregistre un événement lié à l'inscription ou connexion.
     */
    fun logAuthEvent(method: String, success: Boolean) {
        analyticsService?.let { svc ->
            val params = Bundle().apply {
                putString(FirebaseAnalytics.Param.METHOD, method)
                putBoolean("success", success)
            }
            svc.logEvent(FirebaseAnalytics.Event.SIGN_UP, params)
        } ?: run {
            val params = Bundle().apply {
                putString(FirebaseAnalytics.Param.METHOD, method)
                putBoolean("success", success)
            }
            analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, params)
        }
    }

    /**
     * Enregistre une interaction avec une fonctionnalité de l'application.
     */
    fun logFeatureUsage(featureName: String, action: String) {
        analyticsService?.let { svc ->
            val params = Bundle().apply {
                putString("feature_name", featureName)
                putString("action", action)
            }
            svc.logEvent("feature_usage", params)
        } ?: run {
            val params = Bundle().apply {
                putString("feature_name", featureName)
                putString("action", action)
            }
            analytics.logEvent("feature_usage", params)
        }
    }

    /**
     * Enregistre un événement de performance.
     */
    fun logPerformanceEvent(eventName: String, durationMs: Long) {
        analyticsService?.let { svc ->
            val params = Bundle().apply { putLong("duration_ms", durationMs) }
            svc.logEvent(eventName, params)
        } ?: run {
            val params = Bundle().apply { putLong("duration_ms", durationMs) }
            analytics.logEvent(eventName, params)
        }
    }

    /**
     * Définit une propriété utilisateur.
     * Ne pas utiliser pour des informations personnelles identifiables.
     */
    fun setUserProperty(name: String, value: String) {
        analyticsService?.setUserProperty(name, value) ?: analytics.setUserProperty(name, value)
    }

    /**
     * Enregistre un événement personnalisé.
     */
    fun logCustomEvent(eventName: String, params: Bundle?) {
        analyticsService?.logEvent(eventName, params) ?: analytics.logEvent(eventName, params)
    }

    /**
     * Enregistre une sélection de contenu par l'utilisateur.
     */
    fun logSelectContent(contentType: String, itemId: String, itemName: String? = null) {
        analyticsService?.let { svc ->
            val params = Bundle().apply {
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, contentType)
                putString(FirebaseAnalytics.Param.ITEM_ID, itemId)
                itemName?.let { putString(FirebaseAnalytics.Param.ITEM_NAME, it) }
            }
            svc.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, params)
        } ?: run {
            val params = Bundle().apply {
                putString(FirebaseAnalytics.Param.CONTENT_TYPE, contentType)
                putString(FirebaseAnalytics.Param.ITEM_ID, itemId)
                itemName?.let { putString(FirebaseAnalytics.Param.ITEM_NAME, it) }
            }
            analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, params)
        }
    }

    /**
     * Enregistre un événement générique.
     */
    fun logEvent(name: String, params: Bundle?) {
        analyticsService?.logEvent(name, params) ?: analytics.logEvent(name, params)
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
        analyticsService?.logEvent(eventName, params) ?: analytics.logEvent(eventName, params)
    }
}
