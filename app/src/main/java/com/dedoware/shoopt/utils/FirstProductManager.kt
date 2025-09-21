package com.dedoware.shoopt.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Gestionnaire pour suivre les étapes importantes de l'utilisateur,
 * notamment l'ajout du premier produit avec une expérience moderne et engageante
 */
class FirstProductManager private constructor(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val appContext = context.applicationContext

    companion object {
        private const val PREFS_NAME = "shoopt_user_milestones"
        private const val KEY_FIRST_PRODUCT_ADDED = "first_product_added"
        private const val KEY_CONGRATULATION_SHOWN = "congratulation_shown"
        private const val KEY_FIRST_PRODUCT_TIMESTAMP = "first_product_timestamp"

        @Volatile
        private var INSTANCE: FirstProductManager? = null

        fun getInstance(context: Context): FirstProductManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirstProductManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Vérifie si c'est la première fois que l'utilisateur ajoute un produit
     */
    fun isFirstProductEver(): Boolean {
        return !preferences.getBoolean(KEY_FIRST_PRODUCT_ADDED, false)
    }

    /**
     * Marque que l'utilisateur a ajouté son premier produit avec timestamp
     */
    fun markFirstProductAdded() {
        preferences.edit()
            .putBoolean(KEY_FIRST_PRODUCT_ADDED, true)
            .putLong(KEY_FIRST_PRODUCT_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Vérifie si la félicitation a déjà été montrée
     */
    fun wasCongratulationShown(): Boolean {
        return preferences.getBoolean(KEY_CONGRATULATION_SHOWN, false)
    }

    /**
     * Marque que la félicitation a été montrée
     */
    fun markCongratulationShown() {
        preferences.edit()
            .putBoolean(KEY_CONGRATULATION_SHOWN, true)
            .apply()
    }

    /**
     * Obtient le timestamp du premier produit ajouté
     */
    fun getFirstProductTimestamp(): Long {
        return preferences.getLong(KEY_FIRST_PRODUCT_TIMESTAMP, 0L)
    }

    /**
     * Déclenche une vibration haptique de célébration
     */
    fun triggerCelebrationHaptics() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Pattern de vibration sophistiqué pour la célébration
                    val pattern = longArrayOf(0, 100, 50, 200, 50, 300)
                    val amplitudes = intArrayOf(0, 100, 0, 150, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 50, 200, 50, 300), -1)
                }
            }
        } catch (e: Exception) {
            // Ignore si la vibration n'est pas disponible
        }
    }

    /**
     * Détermine si on doit montrer la félicitation
     * (premier produit ET félicitation pas encore montrée)
     */
    fun shouldShowCongratulation(): Boolean {
        return isFirstProductEver() && !wasCongratulationShown()
    }

    /**
     * Réinitialise toutes les préférences (pour les tests ou la démo)
     */
    fun resetAll() {
        preferences.edit()
            .clear()
            .apply()
    }
}
