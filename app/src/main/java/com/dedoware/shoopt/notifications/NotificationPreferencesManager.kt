package com.dedoware.shoopt.notifications

import android.content.Context
import android.content.SharedPreferences
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService

/**
 * Gestionnaire centralisé des préférences de notification
 * Facilite la réutilisabilité et la maintenance des paramètres de notification
 */
class NotificationPreferencesManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "shoopt_notification_preferences"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_SATURDAY_REMINDERS_ENABLED = "saturday_reminders_enabled"
        private const val KEY_FIRST_SETUP_DONE = "notification_first_setup_done"

        // Valeurs par défaut selon la user story
        private const val DEFAULT_REMINDER_HOUR = 9
        private const val DEFAULT_REMINDER_MINUTE = 0

        @Volatile
        private var INSTANCE: NotificationPreferencesManager? = null

        fun getInstance(context: Context): NotificationPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Vérifie si les notifications sont activées globalement
     */
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    /**
     * Active ou désactive les notifications globalement
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .apply()

        val bundle = android.os.Bundle().apply {
            putString("type", "global_notifications")
            putString("enabled", enabled.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_preference_changed", bundle)

        // Programmer ou annuler les rappels selon la nouvelle préférence
        val scheduler = ShoppingReminderScheduler.getInstance(context)
        if (enabled) {
            scheduler.scheduleWeeklyReminders(context)
        } else {
            scheduler.cancelWeeklyReminders(context)
        }
    }

    /**
     * Vérifie si les rappels du samedi sont activés spécifiquement
     */
    fun areSaturdayRemindersEnabled(): Boolean {
        return prefs.getBoolean(KEY_SATURDAY_REMINDERS_ENABLED, true)
    }

    /**
     * Active ou désactive les rappels du samedi
     */
    fun setSaturdayRemindersEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SATURDAY_REMINDERS_ENABLED, enabled)
            .apply()

        val bundle = android.os.Bundle().apply {
            putString("type", "saturday_reminders")
            putString("enabled", enabled.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_preference_changed", bundle)

        // Reprogrammer les rappels si nécessaire
        val scheduler = ShoppingReminderScheduler.getInstance(context)
        if (enabled && areNotificationsEnabled()) {
            scheduler.scheduleWeeklyReminders(context)
        } else {
            scheduler.cancelWeeklyReminders(context)
        }
    }

    /**
     * Récupère l'heure de rappel configurée
     */
    fun getReminderHour(): Int {
        return prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR)
    }

    /**
     * Récupère les minutes de rappel configurées
     */
    fun getReminderMinute(): Int {
        return prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
    }

    /**
     * Met à jour l'heure de rappel
     */
    fun setReminderTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }

        prefs.edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()

        val bundle = android.os.Bundle().apply {
            putString("type", "reminder_time")
            putString("hour", hour.toString())
            putString("minute", minute.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_preference_changed", bundle)

        // Reprogrammer avec la nouvelle heure si les rappels sont activés
        if (areNotificationsEnabled() && areSaturdayRemindersEnabled()) {
            val scheduler = ShoppingReminderScheduler.getInstance(context)
            scheduler.scheduleWeeklyReminders(context)
        }
    }

    /**
     * Vérifie si le premier setup des notifications a été fait
     */
    fun isFirstSetupDone(): Boolean {
        return prefs.getBoolean(KEY_FIRST_SETUP_DONE, false)
    }

    /**
     * Marque le premier setup comme terminé
     */
    fun markFirstSetupDone() {
        prefs.edit()
            .putBoolean(KEY_FIRST_SETUP_DONE, true)
            .apply()

        val bundle = android.os.Bundle().apply {
            putString("reminder_hour", getReminderHour().toString())
            putString("reminder_minute", getReminderMinute().toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_first_setup_completed", bundle)
    }

    /**
     * Vérifie si toutes les conditions pour envoyer une notification sont remplies
     */
    fun shouldSendNotifications(): Boolean {
        return areNotificationsEnabled() && areSaturdayRemindersEnabled()
    }

    /**
     * Retourne un résumé des préférences actuelles
     */
    fun getPreferencesSummary(): Map<String, Any> {
        return mapOf(
            "notifications_enabled" to areNotificationsEnabled(),
            "saturday_reminders_enabled" to areSaturdayRemindersEnabled(),
            "reminder_hour" to getReminderHour(),
            "reminder_minute" to getReminderMinute(),
            "first_setup_done" to isFirstSetupDone(),
            "should_send_notifications" to shouldSendNotifications()
        )
    }

    /**
     * Remet toutes les préférences aux valeurs par défaut
     */
    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, true)
            .putBoolean(KEY_SATURDAY_REMINDERS_ENABLED, true)
            .putInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR)
            .putInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
            .putBoolean(KEY_FIRST_SETUP_DONE, false)
            .apply()

        val bundle = android.os.Bundle().apply {
            putString("reset_to_defaults", "true")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_preferences_reset", bundle)

        // Reprogrammer les rappels avec les valeurs par défaut
        val scheduler = ShoppingReminderScheduler.getInstance(context)
        scheduler.scheduleWeeklyReminders(context)
    }

    /**
     * Formate l'heure de rappel en string lisible
     */
    fun getReminderTimeFormatted(): String {
        val hour = getReminderHour()
        val minute = getReminderMinute()
        return String.format("%02d:%02d", hour, minute)
    }
}
