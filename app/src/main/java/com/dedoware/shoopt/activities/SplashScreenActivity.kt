package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UpdateManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.android.play.core.install.model.InstallStatus
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            setContentView(R.layout.splash_screen)

            // Enregistrement de la vue de l'écran de démarrage dans Analytics
            try {
                AnalyticsManager.logScreenView("SplashScreen", "SplashScreenActivity")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
            }

            forceFullScreen()

            displayVersion()

            // Vérification des mises à jour disponibles
            checkForUpdates()

            redirectToMainScreen()
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans SplashScreenActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "splash_screen_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            // On permet à l'application de continuer malgré l'erreur
            // puisque c'est juste un écran de démarrage
            redirectToLoginOnError()
        }
    }

    // Nouvelle méthode pour vérifier les mises à jour
    private fun checkForUpdates() {
        try {
            val rootView = findViewById<View>(android.R.id.content)
            UpdateManager.checkForUpdate(this, rootView)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la vérification des mises à jour: ${e.message ?: "Message non disponible"}")
        }
    }

    override fun onResume() {
        super.onResume()
        // Vérifier si une mise à jour est en attente d'installation
        try {
            val rootView = findViewById<View>(android.R.id.content)
            UpdateManager.checkForPendingUpdate(this, rootView)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la vérification des mises à jour en attente: ${e.message ?: "Message non disponible"}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Nettoyer les ressources de mise à jour
        UpdateManager.onDestroy()
    }

    private fun redirectToLoginOnError() {
        try {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            // Si même la redirection échoue, on doit simplement terminer l'activité
            finish()
        }
    }

    private fun forceFullScreen() {

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun redirectToMainScreen() {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser

            // Vérifier si l'onboarding a été complété
            val isOnboardingCompleted = UserPreferences.isOnboardingCompleted(this)

            val targetActivity = when {
                !isOnboardingCompleted -> OnboardingActivity::class.java
                currentUser != null -> MainActivity::class.java
                else -> LoginActivity::class.java
            }

            // Analytics pour le statut de connexion au démarrage
            try {
                AnalyticsManager.logUserAction(
                    "app_start",
                    "session",
                    mapOf(
                        "user_status" to if (currentUser != null) "logged_in" else "not_logged_in",
                        "onboarding_completed" to isOnboardingCompleted.toString(),
                        "target_screen" to targetActivity.simpleName
                    )
                )
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
            }

            val executor = Executors.newSingleThreadScheduledExecutor()
            executor.schedule({
                try {
                    val intent = Intent(this, targetActivity)
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    finish()
                } catch (e: Exception) {
                    // Capture des erreurs lors de la redirection
                    CrashlyticsManager.log("Erreur lors de la redirection vers l'écran principal: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "redirect_main_screen")
                    CrashlyticsManager.setCustomKey("target_activity", targetActivity.simpleName)
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    // Tentative de récupération en fermant simplement l'activité
                    finish()
                }
            }, 2000, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // Capture des erreurs lors de la vérification de l'état de l'utilisateur ou la création de l'executor
            CrashlyticsManager.log("Erreur lors de la préparation de la redirection: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "prepare_redirection")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            // Redirection vers l'écran de connexion en cas d'erreur
            redirectToLoginOnError()
        }
    }

    private fun displayVersion() {
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            val versionTextView: TextView = findViewById(R.id.shoopt_version_TV)
            versionTextView.text = "Version: $versionName"
        } catch (e: Exception) {
            // Capture des erreurs lors de l'affichage de la version
            CrashlyticsManager.log("Erreur lors de l'affichage de la version: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "display_version")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            // On ne fait rien de particulier, ce n'est pas une erreur critique
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UpdateManager.UPDATE_REQUEST_CODE) {
            when (resultCode) {
                InstallStatus.FAILED -> {
                    // La mise à jour a échoué
                    CrashlyticsManager.log("La mise à jour in-app a échoué.")
                    val bundle = Bundle()
                    bundle.putString("reason", "update_flow_failed")
                    AnalyticsManager.logEvent("update_failed", bundle)
                }
                InstallStatus.CANCELED -> {
                    // L'utilisateur a annulé la mise à jour
                    AnalyticsManager.logEvent("update_canceled", Bundle())
                }
            }
        }
    }
}
