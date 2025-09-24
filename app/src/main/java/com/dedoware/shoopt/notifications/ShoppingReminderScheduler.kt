package com.dedoware.shoopt.notifications

import android.content.Context
import androidx.work.*
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

class ShoppingReminderScheduler private constructor() {

    companion object {
        private const val WORK_NAME = "saturday_shopping_reminder"
        private const val INITIAL_WORK_NAME = "initial_saturday_shopping_reminder"
        private const val DEFAULT_REMINDER_HOUR = 9
        private const val DEFAULT_REMINDER_MINUTE = 0

        @Volatile
        private var INSTANCE: ShoppingReminderScheduler? = null

        fun getInstance(context: Context): ShoppingReminderScheduler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShoppingReminderScheduler().also { INSTANCE = it }
            }
        }
    }

    /**
     * Programme les rappels hebdomadaires selon les critères de la user story
     * - Chaque samedi à 9h locale
     * - Seulement si l'utilisateur a des listes existantes
     * - Respecte les préférences de notification
     */
    fun scheduleWeeklyReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Annuler les tâches existantes avant de programmer une nouvelle
        cancelWeeklyReminders(context)

        val reminderHour = getReminderHour(context)
        val reminderMinute = getReminderMinute(context)

        // Calculer le délai jusqu'au prochain samedi à l'heure configurée
        val now = LocalDateTime.now()
        val nextSaturday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            .withHour(reminderHour)
            .withMinute(reminderMinute)
            .withSecond(0)
            .withNano(0)

        // Si on est déjà samedi après l'heure configurée, programmer pour le samedi suivant
        val targetDateTime = if (now.isAfter(nextSaturday)) {
            nextSaturday.plusWeeks(1)
        } else {
            nextSaturday
        }

        val delayInMinutes = java.time.Duration.between(now, targetDateTime).toMinutes()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
            .build()

        // Programmer d'abord une tâche unique pour le premier samedi
        val initialWork = OneTimeWorkRequest.Builder(ShoppingReminderWorker::class.java)
            .setInitialDelay(delayInMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag("initial_shopping_reminder")
            .build()

        // Puis programmer la tâche périodique qui démarrera après la première exécution
        val reminderWork = PeriodicWorkRequest.Builder(
            ShoppingReminderWorker::class.java,
            7, TimeUnit.DAYS // Répéter chaque semaine
        )
            .setConstraints(constraints)
            .addTag("shopping_reminder")
            .build()

        // Enqueue les deux tâches
        workManager.enqueue(initialWork)
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            reminderWork
        )

        // Analytics pour tracking de la programmation
        val params = android.os.Bundle().apply {
            putString("day", "saturday")
            putString("hour", reminderHour.toString())
            putString("minute", reminderMinute.toString())
            putString("delay_minutes", delayInMinutes.toString())
            putString("next_execution", targetDateTime.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("reminder_scheduled", params)
    }

    /**
     * Annule tous les rappels programmés
     */
    fun cancelWeeklyReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(INITIAL_WORK_NAME)
        workManager.cancelAllWorkByTag("shopping_reminder")
        workManager.cancelAllWorkByTag("initial_shopping_reminder")

        val params = android.os.Bundle().apply {
            putString("day", "saturday")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("reminder_cancelled", params)
    }

    /**
     * Vérifie si des rappels sont actuellement programmés
     */
    fun isReminderScheduled(context: Context): Boolean {
        val workManager = WorkManager.getInstance(context)
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
            workInfos.any {
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.RUNNING
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Programme une notification immédiate pour les tests
     */
    fun scheduleImmediateTestReminder(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val testWork = OneTimeWorkRequest.Builder(ShoppingReminderWorker::class.java)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .addTag("test_reminder")
            .build()

        workManager.enqueue(testWork)

        val params = android.os.Bundle().apply {
            putString("delay_seconds", "5")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("test_reminder_scheduled", params)
    }

    /**
     * Met à jour l'heure de rappel (pour futures versions avec préférences utilisateur)
     */
    fun updateReminderTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences("shoopt_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()

        // Reprogrammer avec la nouvelle heure
        scheduleWeeklyReminders(context)

        val params = android.os.Bundle().apply {
            putString("new_hour", hour.toString())
            putString("new_minute", minute.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("reminder_time_updated", params)
    }

    /**
     * Active ou désactive les rappels
     */
    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("shoopt_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("notifications_enabled", enabled)
            .apply()

        if (enabled) {
            scheduleWeeklyReminders(context)
        } else {
            cancelWeeklyReminders(context)
        }

        val params = android.os.Bundle().apply {
            putString("enabled", enabled.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("reminders_toggled", params)
    }

    /**
     * Récupère l'heure de rappel configurée (défaut : 9h)
     */
    private fun getReminderHour(context: Context): Int {
        val prefs = context.getSharedPreferences("shoopt_preferences", Context.MODE_PRIVATE)
        return prefs.getInt("reminder_hour", DEFAULT_REMINDER_HOUR)
    }

    /**
     * Récupère les minutes de rappel configurées (défaut : 0)
     */
    private fun getReminderMinute(context: Context): Int {
        val prefs = context.getSharedPreferences("shoopt_preferences", Context.MODE_PRIVATE)
        return prefs.getInt("reminder_minute", DEFAULT_REMINDER_MINUTE)
    }

    /**
     * Retourne les informations sur le prochain rappel programmé
     */
    fun getNextReminderInfo(context: Context): Map<String, Any> {
        val hour = getReminderHour(context)
        val minute = getReminderMinute(context)
        val isScheduled = isReminderScheduled(context)

        val now = LocalDateTime.now()
        val nextSaturday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        val targetDateTime = if (now.isAfter(nextSaturday)) {
            nextSaturday.plusWeeks(1)
        } else {
            nextSaturday
        }

        return mapOf(
            "is_scheduled" to isScheduled,
            "next_execution" to targetDateTime.toString(),
            "hour" to hour,
            "minute" to minute,
            "days_until" to java.time.Duration.between(now, targetDateTime).toDays()
        )
    }
}
