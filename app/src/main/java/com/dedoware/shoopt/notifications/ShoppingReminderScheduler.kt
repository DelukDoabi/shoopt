package com.dedoware.shoopt.notifications

import android.content.Context
import androidx.work.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

class ShoppingReminderScheduler private constructor() {

    companion object {
        private const val WORK_NAME = "saturday_shopping_reminder"

        @Volatile
        private var INSTANCE: ShoppingReminderScheduler? = null

        fun getInstance(context: Context): ShoppingReminderScheduler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShoppingReminderScheduler().also { INSTANCE = it }
            }
        }
    }

    fun scheduleWeeklyReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Annuler les tâches existantes avant de programmer une nouvelle
        cancelWeeklyReminders(context)

        // Calculer le délai jusqu'au prochain samedi à 9h
        val now = LocalDateTime.now()
        val nextSaturday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            .withHour(9)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // Si on est déjà samedi après 9h, programmer pour le samedi suivant
        val targetDateTime = if (now.isAfter(nextSaturday)) {
            nextSaturday.plusWeeks(1)
        } else {
            nextSaturday
        }

        val delayInMinutes = java.time.Duration.between(now, targetDateTime).toMinutes()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        // Programmer d'abord une tâche unique pour le premier samedi
        val initialWork = OneTimeWorkRequest.Builder(ShoppingReminderWorker::class.java)
            .setInitialDelay(delayInMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag("initial_shopping_reminder")
            .build()

        // Puis programmer la tâche périodique sans délai initial
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
    }

    fun cancelWeeklyReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun isReminderScheduled(context: Context): Boolean {
        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
        return workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
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
    }
}
