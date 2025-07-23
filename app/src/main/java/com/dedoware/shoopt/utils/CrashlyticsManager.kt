package com.dedoware.shoopt.utils

import android.os.Bundle
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.lang.Exception

/**
 * Gestionnaire pour Firebase Crashlytics qui offre des méthodes utilitaires
 * pour enregistrer des erreurs, des exceptions et des informations utilisateur.
 */
object CrashlyticsManager {
    private val crashlytics: FirebaseCrashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    /**
     * Active ou désactive la collecte des rapports de crash.
     * Utile pour désactiver en mode développement ou selon les préférences de l'utilisateur.
     */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    /**
     * Enregistre l'identifiant de l'utilisateur pour les rapports de crash.
     * Aide à identifier les problèmes spécifiques à un utilisateur.
     */
    fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }

    /**
     * Enregistre une exception non fatale (qui ne fait pas planter l'app).
     * Utile pour suivre les erreurs récupérables.
     */
    fun logException(exception: Exception) {
        crashlytics.recordException(exception)
    }

    /**
     * Enregistre un log personnalisé qui apparaîtra dans les rapports de crash.
     */
    fun log(message: String) {
        crashlytics.log(message)
    }

    /**
     * Définit une clé personnalisée et sa valeur qui seront incluses dans les rapports.
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Définit une clé personnalisée et sa valeur booléenne.
     */
    fun setCustomKey(key: String, value: Boolean) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Définit une clé personnalisée et sa valeur numérique.
     */
    fun setCustomKey(key: String, value: Int) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Définit une clé personnalisée et sa valeur numérique longue.
     */
    fun setCustomKey(key: String, value: Long) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Définit une clé personnalisée et sa valeur numérique flottante.
     */
    fun setCustomKey(key: String, value: Float) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Définit une clé personnalisée et sa valeur numérique double.
     */
    fun setCustomKey(key: String, value: Double) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Définit plusieurs clés personnalisées en une seule fois.
     */
    fun setCustomKeys(keysAndValues: CustomKeysAndValues) {
        crashlytics.setCustomKeys(keysAndValues)
    }

    /**
     * Enregistre les étapes menant à un crash pour mieux comprendre le contexte.
     * Utilisez des numéros séquentiels pour suivre l'ordre des événements.
     */
    fun setCustomBreadcrumb(breadcrumb: String) {
        log("Breadcrumb: $breadcrumb")
    }

    /**
     * Efface toutes les données de crash enregistrées.
     */
    fun clearAllReports() {
        crashlytics.deleteUnsentReports()
    }

    /**
     * Force l'envoi immédiat de tous les rapports stockés.
     */
    fun sendReports() {
        crashlytics.sendUnsentReports()
    }
}
