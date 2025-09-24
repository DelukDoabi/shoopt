package com.dedoware.shoopt.notifications

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.UserPreferences

/**
 * Classe responsable de la gestion des permissions de notification
 * Vérifie si les notifications sont activées et aide l'utilisateur à les activer si besoin
 */
class NotificationPermissionManager private constructor(private val context: Context) {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val PREF_DONT_ASK_NOTIFICATIONS = "pref_dont_ask_notifications"

        @Volatile
        private var INSTANCE: NotificationPermissionManager? = null

        fun getInstance(context: Context): NotificationPermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationPermissionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManager = NotificationManager.getInstance(context)
    private val userPreferences = UserPreferences.getInstance(context)

    /**
     * Vérifie si les notifications sont activées, sinon guide l'utilisateur
     * @param activity L'activité actuelle pour afficher le dialogue
     * @param forceShow Force l'affichage du dialogue même si l'utilisateur a choisi de ne plus être demandé
     */
    fun checkNotificationPermission(activity: Activity, forceShow: Boolean = false) {
        // Vérifie si on doit ignorer la demande (si l'utilisateur a déjà refusé et a coché "ne plus demander")
        if (!forceShow && UserPreferences.getBooleanPreference(context, PREF_DONT_ASK_NOTIFICATIONS, false)) {
            return
        }

        // Si les notifications ne sont pas activées, affiche un dialogue pour guider l'utilisateur
        if (!notificationManager.areNotificationsEnabled()) {
            showNotificationPermissionDialog(activity)
        }
    }

    /**
     * Affiche un dialogue personnalisé invitant l'utilisateur à activer les notifications
     */
    private fun showNotificationPermissionDialog(activity: Activity) {
        // Création du dialogue avec notre layout personnalisé
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_notification_permission, null)
        val dialog = Dialog(activity)

        // Configuration du dialogue
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Récupération des éléments du dialogue
        val btnAccept = dialogView.findViewById<android.widget.Button>(R.id.btn_accept)
        val btnDecline = dialogView.findViewById<android.widget.Button>(R.id.btn_decline)
        val dontAskCheckbox = dialogView.findViewById<CheckBox>(R.id.dont_ask_again_checkbox)

        // Configuration des boutons
        btnAccept.setOnClickListener {
            // Redirige vers les paramètres de l'application
            openNotificationSettings(activity)

            // Analytique pour suivre l'acceptation de l'utilisateur
            AnalyticsManager.trackEvent("notification_permission_accepted", mapOf(
                "source" to "dialog",
                "app_version" to getAppVersion(context)
            ))

            dialog.dismiss()
        }

        btnDecline.setOnClickListener {
            // Analytique pour suivre le refus de l'utilisateur
            AnalyticsManager.trackEvent("notification_permission_declined", mapOf(
                "source" to "dialog",
                "app_version" to getAppVersion(context)
            ))

            // Sauvegarde du choix "ne plus demander" si coché
            if (dontAskCheckbox.isChecked) {
                UserPreferences.setBooleanPreference(context, PREF_DONT_ASK_NOTIFICATIONS, true)

                // Analytique pour suivre le choix de ne plus voir la demande
                AnalyticsManager.trackEvent("notification_permission_dont_ask", mapOf(
                    "app_version" to getAppVersion(context)
                ))
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Ouvre les paramètres de l'application pour permettre à l'utilisateur d'activer les notifications
     */
    private fun openNotificationSettings(activity: Activity) {
        val intent = Intent()

        // Sur Android 8.0 (API 26) et supérieur, redirige vers les paramètres du canal de notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        } else {
            // Pour les versions antérieures
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.parse("package:" + activity.packageName)
        }

        activity.startActivity(intent)
    }

    /**
     * Vérifie l'état des notifications après que l'utilisateur revient des paramètres
     */
    fun checkNotificationStatusAfterSettings(activity: Activity) {
        if (notificationManager.areNotificationsEnabled()) {
            // Les notifications sont désormais activées
            Toast.makeText(
                activity,
                R.string.notifications_enabled_message,
                Toast.LENGTH_SHORT
            ).show()

            // Analytique pour suivre l'activation réussie des notifications
            AnalyticsManager.trackEvent("notification_permission_enabled", mapOf(
                "source" to "settings",
                "app_version" to getAppVersion(context)
            ))
        } else {
            // Les notifications sont toujours désactivées
            Toast.makeText(
                activity,
                R.string.notifications_disabled_message,
                Toast.LENGTH_LONG
            ).show()

            // Analytique pour suivre que les notifications restent désactivées
            AnalyticsManager.trackEvent("notification_permission_still_disabled", mapOf(
                "source" to "settings",
                "app_version" to getAppVersion(context)
            ))
        }
    }

    /**
     * Récupère la version de l'application pour les données analytiques
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown" // Gestion du cas où versionName est null
        } catch (e: Exception) {
            "unknown"
        }
    }
}
