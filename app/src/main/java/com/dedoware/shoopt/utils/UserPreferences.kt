package com.dedoware.shoopt.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class UserPreferences(context: Context) {
    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_CURRENCY = "currency"

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
