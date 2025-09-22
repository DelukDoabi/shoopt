package com.dedoware.shoopt.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.utils.AnalyticsManager
import kotlinx.coroutines.runBlocking

class ShoppingReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        return try {
            // Vérifier les conditions avant d'envoyer la notification
            if (!shouldSendReminder()) {
                // Log pourquoi la notification n'a pas été envoyée
                AnalyticsManager.trackEvent("notification_skipped", mapOf(
                    "reason" to getSkipReason(),
                    "day" to "saturday"
                ))
                return Result.success()
            }

            // Créer et afficher la notification de rappel
            val notificationManager = NotificationManager.getInstance(applicationContext)
            notificationManager.showShoppingReminder()

            // Analytics pour tracking
            AnalyticsManager.trackEvent("notification_sent", mapOf(
                "type" to "shopping_reminder",
                "day" to "saturday",
                "lists_count" to getShoppingListsCount().toString()
            ))

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            AnalyticsManager.trackEvent("notification_error", mapOf(
                "error" to (e.message ?: "unknown_error"),
                "day" to "saturday"
            ))
            Result.failure()
        }
    }

    /**
     * Vérifie si la notification de rappel doit être envoyée
     * Critères de la user story :
     * - L'utilisateur doit avoir au moins 1 liste existante
     * - Les notifications ne doivent pas être désactivées
     */
    private fun shouldSendReminder(): Boolean {
        val prefsManager = NotificationPreferencesManager.getInstance(applicationContext)
        return hasExistingShoppingLists() && prefsManager.shouldSendNotifications()
    }

    /**
     * Vérifie si l'utilisateur a au moins une liste de courses
     * Critère d'acceptation : "L'utilisateur doit avoir au moins 1 liste existante"
     */
    private fun hasExistingShoppingLists(): Boolean {
        return runBlocking {
            try {
                val database = ShooptRoomDatabase.getDatabase(applicationContext)
                val listsCount = database.shoppingListDao().getShoppingListsCount()
                listsCount > 0
            } catch (e: Exception) {
                // En cas d'erreur, on considère qu'il n'y a pas de listes pour éviter le spam
                AnalyticsManager.trackEvent("database_error_in_reminder", mapOf(
                    "error" to (e.message ?: "unknown_error")
                ))
                false
            }
        }
    }

    /**
     * Retourne le nombre de listes de courses pour analytics
     */
    private fun getShoppingListsCount(): Int {
        return runBlocking {
            try {
                val database = ShooptRoomDatabase.getDatabase(applicationContext)
                database.shoppingListDao().getShoppingListsCount()
            } catch (e: Exception) {
                0
            }
        }
    }

    /**
     * Retourne la raison pour laquelle la notification a été ignorée
     */
    private fun getSkipReason(): String {
        val prefsManager = NotificationPreferencesManager.getInstance(applicationContext)
        return when {
            !hasExistingShoppingLists() -> "no_lists"
            !prefsManager.areNotificationsEnabled() -> "notifications_disabled"
            !prefsManager.areSaturdayRemindersEnabled() -> "saturday_reminders_disabled"
            else -> "unknown"
        }
    }
}
