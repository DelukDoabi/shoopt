package com.dedoware.shoopt.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ShoppingReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        return try {
            // Créer et afficher la notification de rappel
            val notificationManager = com.dedoware.shoopt.notifications.NotificationManager.getInstance(applicationContext)
            notificationManager.showShoppingReminder()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
