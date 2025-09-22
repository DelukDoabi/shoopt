package com.dedoware.shoopt.admin

import android.content.Context
import android.content.SharedPreferences
import com.dedoware.shoopt.utils.AnalyticsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gestionnaire moderne pour les privilèges administrateur
 *
 * Méthodes supportées :
 * 1. Firebase Remote Config (recommandé) - Configuration centralisée
 * 2. Liste d'emails admin - Basé sur l'authentification Firebase
 * 3. Code secret local - Pour les tests en développement
 */
class AdminManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "shoopt_admin_prefs"
        private const val KEY_IS_ADMIN_LOCALLY = "is_admin_locally"
        private const val KEY_ADMIN_CODE_ENTERED = "admin_code_entered"
        private const val KEY_LAST_ADMIN_CHECK = "last_admin_check"

        // Code secret pour dev (à changer en production)
        private const val ADMIN_SECRET_CODE = "SHOOPT_DEV_2024"

        // Configuration Firebase Remote Config
        private const val REMOTE_CONFIG_ADMIN_EMAILS = "admin_emails"
        private const val REMOTE_CONFIG_ADMIN_ENABLED = "admin_features_enabled"

        @Volatile
        private var INSTANCE: AdminManager? = null

        fun getInstance(context: Context): AdminManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdminManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    init {
        setupRemoteConfig()
    }

    /**
     * Configure Firebase Remote Config pour la gestion des admins
     */
    private fun setupRemoteConfig() {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (isDebugMode()) 0 else 3600) // 1h en prod, immédiat en dev
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        // Valeurs par défaut
        val defaults = mapOf(
            REMOTE_CONFIG_ADMIN_EMAILS to "", // Liste d'emails séparés par des virgules
            REMOTE_CONFIG_ADMIN_ENABLED to true
        )
        remoteConfig.setDefaultsAsync(defaults)

        // Fetch automatique au démarrage
        CoroutineScope(Dispatchers.IO).launch {
            try {
                remoteConfig.fetchAndActivate().await()
            } catch (e: Exception) {
                // Fallback silencieux sur les valeurs locales
            }
        }
    }

    /**
     * Vérifie si l'utilisateur actuel est administrateur
     * Utilise plusieurs méthodes en cascade
     */
    suspend fun isCurrentUserAdmin(): Boolean {
        val currentUser = auth.currentUser ?: return false

        try {
            // 1. Vérification via Remote Config (priorité)
            if (isAdminViaRemoteConfig(currentUser.email)) {
                trackAdminAccess("remote_config")
                return true
            }

            // 2. Vérification via liste locale d'emails admin
            if (isAdminViaEmailList(currentUser.email)) {
                trackAdminAccess("email_list")
                return true
            }

            // 3. Vérification via code secret (développement uniquement)
            if (isDebugMode() && isAdminViaLocalCode()) {
                trackAdminAccess("local_code")
                return true
            }

            return false
        } catch (e: Exception) {
            // En cas d'erreur, fallback sur les préférences locales
            return prefs.getBoolean(KEY_IS_ADMIN_LOCALLY, false)
        }
    }

    /**
     * Méthode 1: Vérification via Firebase Remote Config
     */
    private suspend fun isAdminViaRemoteConfig(userEmail: String?): Boolean {
        if (userEmail == null) return false

        try {
            // Fetch les dernières valeurs
            remoteConfig.fetchAndActivate().await()

            val adminEmails = remoteConfig.getString(REMOTE_CONFIG_ADMIN_EMAILS)
            val adminEnabled = remoteConfig.getBoolean(REMOTE_CONFIG_ADMIN_ENABLED)

            if (!adminEnabled) return false

            return adminEmails.split(",")
                .map { it.trim().lowercase() }
                .contains(userEmail.lowercase())
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Méthode 2: Liste locale d'emails administrateur
     */
    private fun isAdminViaEmailList(userEmail: String?): Boolean {
        if (userEmail == null) return false

        // Liste des emails admin (à personnaliser)
        val adminEmails = setOf(
            "deluk.doabi@gmail.com",
            // Ajoutez d'autres emails admin ici
        )

        return adminEmails.contains(userEmail.lowercase())
    }

    /**
     * Méthode 3: Code secret pour développement
     */
    private fun isAdminViaLocalCode(): Boolean {
        return prefs.getBoolean(KEY_ADMIN_CODE_ENTERED, false)
    }

    /**
     * Permet à un utilisateur de devenir admin via code secret (développement)
     */
    fun enterAdminCode(code: String): Boolean {
        if (code == ADMIN_SECRET_CODE) {
            prefs.edit()
                .putBoolean(KEY_ADMIN_CODE_ENTERED, true)
                .putBoolean(KEY_IS_ADMIN_LOCALLY, true)
                .apply()

            trackAdminAccess("secret_code")
            return true
        }
        return false
    }

    /**
     * Révoque les privilèges admin locaux
     */
    fun revokeLocalAdminPrivileges() {
        prefs.edit()
            .putBoolean(KEY_ADMIN_CODE_ENTERED, false)
            .putBoolean(KEY_IS_ADMIN_LOCALLY, false)
            .apply()

        AnalyticsManager.trackEvent("admin_privileges_revoked", mapOf(
            "method" to "local_revoke"
        ))
    }

    /**
     * Vérifie si l'app est en mode debug
     */
    private fun isDebugMode(): Boolean {
        return try {
            val buildConfig = Class.forName("${context.packageName}.BuildConfig")
            val debugField = buildConfig.getField("DEBUG")
            debugField.getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Track l'accès admin pour analytics
     */
    private fun trackAdminAccess(method: String) {
        AnalyticsManager.trackEvent("admin_access_granted", mapOf(
            "method" to method,
            "user_email" to (auth.currentUser?.email ?: "unknown"),
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }

    /**
     * Interface pour gérer les privilèges admin via Remote Config
     */
    suspend fun updateRemoteAdminList(newAdminEmails: List<String>): Boolean {
        // Cette méthode serait implémentée côté serveur
        // Pour l'instant, elle sert de placeholder pour la documentation
        return false
    }

    /**
     * Obtient des informations de débogage sur le statut admin
     */
    suspend fun getAdminDebugInfo(): Map<String, Any> {
        val currentUser = auth.currentUser
        return mapOf(
            "current_user_email" to (currentUser?.email ?: "not_logged_in"),
            "is_admin_remote_config" to isAdminViaRemoteConfig(currentUser?.email),
            "is_admin_email_list" to isAdminViaEmailList(currentUser?.email),
            "is_admin_local_code" to isAdminViaLocalCode(),
            "is_debug_mode" to isDebugMode(),
            "last_check_timestamp" to prefs.getLong(KEY_LAST_ADMIN_CHECK, 0)
        )
    }
}
