package com.dedoware.shoopt.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dedoware.shoopt.R
import com.dedoware.shoopt.activities.MainActivity
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ShooptFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "shopping_reminder_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Traiter les notifications de rappel de courses
        when (remoteMessage.data["type"]) {
            "shopping_reminder" -> {
                showShoppingReminderNotification(
                    title = remoteMessage.data["title"] ?: getString(R.string.notification_shopping_reminder_title),
                    body = remoteMessage.data["body"] ?: getString(R.string.notification_shopping_reminder_body)
                )

                // Analytics pour tracking des notifications reçues
                val params = android.os.Bundle().apply {
                    putString("type", "shopping_reminder")
                    putString("day", "saturday")
                    putString("source", "fcm")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_received", params)
            }
            "custom_reminder" -> {
                showCustomReminderNotification(remoteMessage)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Envoyer le token au serveur pour l'envoi de notifications ciblées
        sendTokenToServer(token)

        // Analytics pour tracking des nouveaux tokens
        val params = android.os.Bundle().apply {
            putString("token_length", token.length.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("fcm_token_refreshed", params)
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_shopping_reminders)
        val descriptionText = getString(R.string.notification_channel_shopping_reminders_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            setShowBadge(true)
            enableLights(true)
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showShoppingReminderNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Navigation directe vers l'écran Liste de courses comme spécifié dans la user story
            putExtra("navigate_to", "shopping_list")
            putExtra("notification_source", "saturday_reminder_fcm")
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shopping_cart_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(getString(R.string.notification_shopping_reminder_big_text)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_list,
                getString(R.string.notification_action_view_list),
                createViewListPendingIntent()
            )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())

        // Analytics pour tracking des notifications affichées via FCM
        val params = android.os.Bundle().apply {
            putString("type", "shopping_reminder")
            putString("day", "saturday")
            putString("source", "fcm")
            putString("title_length", title.length.toString())
            putString("body_length", body.length.toString())
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_displayed", params)
    }

    private fun showCustomReminderNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: getString(R.string.notification_custom_reminder_title)
        val body = remoteMessage.data["body"] ?: getString(R.string.notification_custom_reminder_body)
        val actionText = remoteMessage.data["action_text"] ?: getString(R.string.notification_action_view_list)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", remoteMessage.data["navigate_to"] ?: "shopping_list")
            putExtra("notification_source", "custom_reminder_fcm")
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shopping_cart_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_list,
                actionText,
                pendingIntent
            )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())

        val params = android.os.Bundle().apply {
            putString("type", "custom_reminder")
            putString("source", "fcm")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("notification_displayed", params)
    }

    private fun createViewListPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "shopping_list")
            putExtra("notification_source", "saturday_reminder_action")
        }

        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sendTokenToServer(token: String) {
        // TODO: Implémenter l'envoi du token à votre serveur backend
        // pour l'envoi de notifications ciblées
        // Exemple : ApiService.sendFCMToken(token)
        val params = android.os.Bundle().apply {
            putString("token_generated", "true")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("fcm_token_ready", params)
    }
}
