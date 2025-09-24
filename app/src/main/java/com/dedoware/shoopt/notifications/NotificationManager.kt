package com.dedoware.shoopt.notifications

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dedoware.shoopt.R
import com.dedoware.shoopt.activities.MainActivity
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService

class NotificationManager private constructor(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "shopping_reminder_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var INSTANCE: NotificationManager? = null

        fun getInstance(context: Context): NotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_shopping_reminders),
            AndroidNotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_shopping_reminders_description)
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Affiche la notification de rappel de courses avec le message exact de la user story
     */
    fun showShoppingReminder() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Navigation directe vers l'écran Liste de courses
            putExtra("navigate_to", "shopping_list")
            putExtra("notification_source", "saturday_reminder")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shopping_cart_notification)
            .setContentTitle(context.getString(R.string.notification_shopping_reminder_title))
            .setContentText(context.getString(R.string.notification_shopping_reminder_body))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notification_shopping_reminder_big_text)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_list,
                context.getString(R.string.notification_action_view_list),
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)

        // Analytics pour tracking des notifications affichées
        val bundle = android.os.Bundle().apply {
            putString("type", "shopping_reminder")
            putString("day", "saturday")
            putString("message_type", "saturday_9am")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_displayed", bundle)
    }

    /**
     * Affiche une notification de rappel personnalisée (pour usage futur)
     */
    fun showCustomReminder(title: String, message: String, actionText: String = context.getString(R.string.notification_action_view_list)) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "shopping_list")
            putExtra("notification_source", "custom_reminder")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shopping_cart_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_list,
                actionText,
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)

        val bundle = android.os.Bundle().apply {
            putString("type", "custom_reminder")
            putString("title", title)
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_displayed", bundle)
    }

    /**
     * Vérifie si les notifications sont autorisées par le système
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
}
