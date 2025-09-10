package com.dedoware.shoopt.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class UserPreferences(context: Context) {
    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ONBOARDING_VERSION = "onboarding_version"
        private const val CURRENT_ONBOARDING_VERSION = 1 // Incrémenter quand l'onboarding change

        // Spotlight system constants
        private const val KEY_SPOTLIGHT_PREFIX = "spotlight_seen_"
        private const val KEY_SPOTLIGHT_VERSION = "spotlight_version"
        private const val CURRENT_SPOTLIGHT_VERSION = 1 // Incrémenter pour forcer l'affichage

        // Autocompletion constants
        private const val KEY_AI_AUTOCOMPLETION_ENABLED = "ai_autocompletion_enabled"
        private const val KEY_MAPS_AUTOCOMPLETION_ENABLED = "maps_autocompletion_enabled"
        private const val KEY_PHOTO_TIP_ENABLED = "photo_tip_enabled"

        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_SYSTEM = 0

        // Valeurs par défaut
        const val DEFAULT_THEME = THEME_SYSTEM
        const val DEFAULT_CURRENCY = "EUR"

        // Map des devises avec leurs symboles
        private val CURRENCY_SYMBOLS = mapOf(
            "EUR" to "€",
            "USD" to "$",
            "GBP" to "£",
            "JPY" to "¥",
            "CAD" to "C$",
            "CHF" to "Fr",
            "AUD" to "A$"
        )

        // Nouvelle méthode statique pour l'onboarding
        fun setOnboardingCompleted(context: Context, completed: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
                .putInt(KEY_ONBOARDING_VERSION, CURRENT_ONBOARDING_VERSION)
                .apply()
        }

        fun isOnboardingCompleted(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
            val version = prefs.getInt(KEY_ONBOARDING_VERSION, 0)

            // Si la version de l'onboarding a changé, considérer comme non complété
            return completed && version >= CURRENT_ONBOARDING_VERSION
        }

        /**
         * Vérifie si le spotlight doit être affiché pour un écran donné
         * Le spotlight s'affiche seulement après l'onboarding principal
         */
        fun shouldShowSpotlight(context: Context, screenKey: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Vérifier s'il y a un flag de forçage pour les spotlights (après replay onboarding)
            val isForced = prefs.getBoolean("force_spotlights_on_next_resume", false)
            if (isForced) {
                return true
            }

            // Vérifier que l'onboarding principal est terminé
            if (!isOnboardingCompleted(context)) {
                return false
            }

            // Vérifier si le spotlight a déjà été vu pour cet écran
            val spotlightSeen = prefs.getBoolean(KEY_SPOTLIGHT_PREFIX + screenKey, false)
            val currentVersion = prefs.getInt(KEY_SPOTLIGHT_VERSION, 0)

            // Afficher si pas encore vu ou si la version a changé
            return !spotlightSeen || currentVersion < CURRENT_SPOTLIGHT_VERSION
        }

        /**
         * Marque le spotlight comme vu pour un écran donné
         */
        fun markSpotlightSeen(context: Context, screenKey: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_SPOTLIGHT_PREFIX + screenKey, true)
                .putInt(KEY_SPOTLIGHT_VERSION, CURRENT_SPOTLIGHT_VERSION)
                .apply()
        }

        /**
         * Réinitialise tous les spotlights (utile pour les tests ou les démos)
         */
        fun resetAllSpotlights(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Supprimer tous les flags de spotlight
            for (key in prefs.all.keys) {
                if (key.startsWith(KEY_SPOTLIGHT_PREFIX)) {
                    editor.remove(key)
                }
            }

            // Forcer le réaffichage des spotlights au prochain lancement
            editor.putBoolean("force_spotlights_on_next_resume", true)
            editor.apply()
        }

        // Méthodes pour l'autocomplétion AI
        fun isAiAutocompletionEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AI_AUTOCOMPLETION_ENABLED, true) // Activé par défaut
        }

        fun setAiAutocompletionEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_AI_AUTOCOMPLETION_ENABLED, enabled).apply()
        }

        // Méthodes pour l'autocomplétion Maps
        fun isMapsAutocompletionEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MAPS_AUTOCOMPLETION_ENABLED, true) // Activé par défaut
        }

        fun setMapsAutocompletionEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MAPS_AUTOCOMPLETION_ENABLED, enabled).apply()
        }

        // Méthodes pour les conseils photo
        fun shouldShowPhotoTips(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PHOTO_TIP_ENABLED, true) // Activé par défaut
        }

        fun setPhotoTipsEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PHOTO_TIP_ENABLED, enabled).apply()
        }
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: Int
        get() = preferences.getInt(KEY_THEME, DEFAULT_THEME)
        set(value) = preferences.edit().putInt(KEY_THEME, value).apply()

    var currency: String
        get() = preferences.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        set(value) = preferences.edit().putString(KEY_CURRENCY, value).apply()

    fun applyTheme() {
        val nightMode = when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun getCurrencySymbol(): String {
        return CURRENCY_SYMBOLS[currency] ?: "€"
    }

    fun formatPrice(price: Double): String {
        return String.format("%.2f %s", price, getCurrencySymbol())
    }
}
