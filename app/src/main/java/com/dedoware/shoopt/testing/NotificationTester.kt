package com.dedoware.shoopt.testing

import android.content.Context
import com.dedoware.shoopt.notifications.ShoppingReminderScheduler
import com.dedoware.shoopt.notifications.NotificationManager
import com.dedoware.shoopt.notifications.NotificationPreferencesManager

/**
 * Classe utilitaire pour tester les notifications de rappel courses
 */
object NotificationTester {

    /**
     * Teste immédiatement la notification (dans 5 secondes)
     */
    fun testImmediateNotification(context: Context) {
        val scheduler = ShoppingReminderScheduler.getInstance(context)
        scheduler.scheduleImmediateTestReminder(context)
        println("🧪 Test de notification programmé pour dans 5 secondes")
    }

    /**
     * Teste directement l'affichage de la notification
     */
    fun testNotificationDisplay(context: Context) {
        val notificationManager = NotificationManager.getInstance(context)
        notificationManager.showShoppingReminder()
        println("🧪 Notification de test affichée immédiatement")
    }

    /**
     * Teste une notification personnalisée
     */
    fun testCustomNotification(context: Context, title: String, message: String) {
        val notificationManager = NotificationManager.getInstance(context)
        notificationManager.showCustomReminder(title, message)
        println("🧪 Notification personnalisée affichée : $title")
    }

    /**
     * Vérifie l'état des préférences de notification
     */
    fun checkNotificationSettings(context: Context): Map<String, Any> {
        val prefsManager = NotificationPreferencesManager.getInstance(context)
        return prefsManager.getPreferencesSummary()
    }

    /**
     * Vérifie les informations du prochain rappel programmé
     */
    fun checkScheduledReminder(context: Context): Map<String, Any> {
        val scheduler = ShoppingReminderScheduler.getInstance(context)
        return scheduler.getNextReminderInfo(context)
    }

    /**
     * Teste le scénario complet : vérification des conditions + notification
     */
    fun testCompleteScenario(context: Context): String {
        val prefsManager = NotificationPreferencesManager.getInstance(context)
        val scheduler = ShoppingReminderScheduler.getInstance(context)

        return buildString {
            appendLine("🧪 TEST COMPLET DE LA FONCTIONNALITÉ")
            appendLine("=====================================")

            // Vérifier les préférences
            val prefs = prefsManager.getPreferencesSummary()
            appendLine("✅ Notifications activées: ${prefs["notifications_enabled"]}")
            appendLine("✅ Rappels samedi activés: ${prefs["saturday_reminders_enabled"]}")
            appendLine("✅ Heure de rappel: ${prefsManager.getReminderTimeFormatted()}")

            // Vérifier la programmation
            val reminderInfo = scheduler.getNextReminderInfo(context)
            appendLine("✅ Rappel programmé: ${reminderInfo["is_scheduled"]}")
            appendLine("✅ Prochaine exécution: ${reminderInfo["next_execution"]}")
            appendLine("✅ Jours restants: ${reminderInfo["days_until"]}")

            // Test de notification
            appendLine("\n🧪 Lancement du test de notification...")
            testImmediateNotification(context)
            appendLine("✅ Notification de test programmée pour 5 secondes")
        }
    }
}
