package com.dedoware.shoopt.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dedoware.shoopt.R
import com.dedoware.shoopt.activities.MainActivity
import com.dedoware.shoopt.utils.AnalyticsManager
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
        if (remoteMessage.data["type"] == "shopping_reminder") {
            showShoppingReminderNotification(
                title = remoteMessage.data["title"] ?: "🛒 Votre liste Shoopt est prête !",
                body = remoteMessage.data["body"] ?: "Vérifiez votre liste pour vos courses du samedi"
            )

            // Analytics pour tracking
            AnalyticsManager.trackEvent("notification_received", mapOf(
                "type" to "shopping_reminder",
                "day" to "saturday"
            ))
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Envoyer le token au serveur pour l'envoi de notifications ciblées
        sendTokenToServer(token)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Rappels de courses"
            val descriptionText = "Notifications de rappel pour vos listes de courses"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showShoppingReminderNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_shopping_list", true)
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
                "Voir ma liste",
                pendingIntent
            )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())

        // Analytics pour tracking des clics
        AnalyticsManager.trackEvent("notification_displayed", mapOf(
            "type" to "shopping_reminder",
            "day" to "saturday"
        ))
    }

    private fun sendTokenToServer(token: String) {
        // Ici vous pouvez envoyer le token à votre serveur
        // pour l'envoi de notifications ciblées
    }
}
