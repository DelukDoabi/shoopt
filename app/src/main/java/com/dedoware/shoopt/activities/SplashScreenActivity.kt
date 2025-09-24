package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import com.dedoware.shoopt.R
import com.dedoware.shoopt.analytics.AnalyticsConsentDialogFragment
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.notifications.NotificationPermissionManager
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UpdateCallback
import com.dedoware.shoopt.utils.UpdateManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.android.play.core.install.model.InstallStatus
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class SplashScreenActivity : AppCompatActivity(), UpdateCallback, AnalyticsConsentDialogFragment.ConsentListener {

    private var updateCheckComplete = false
    private var minSplashDurationComplete = false
    private var updateDialogShowing = false // Variable pour suivre si le dialogue de mise à jour est visible
    private var analyticsConsentHandled = false // Variable pour suivre si le consentement analytics a été traité
    private val MIN_SPLASH_DURATION_MS = 1500L // Durée minimum du splash screen en millisecondes
    private lateinit var notificationPermissionManager: NotificationPermissionManager
    private lateinit var analyticsService: AnalyticsService

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            setContentView(R.layout.splash_screen)

            // Initialisation du gestionnaire de permissions de notification
            notificationPermissionManager = NotificationPermissionManager.getInstance(this)

            // Enregistrement de la vue de l'écran de démarrage dans Analytics
            try {
                AnalyticsManager.logScreenView("SplashScreen", "SplashScreenActivity")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
            }

            forceFullScreen()

            displayVersion()

            // Initialiser le gestionnaire de mises à jour
            UpdateManager.init(this)

            // Vérification des mises à jour disponibles avec callback
            checkForUpdates()

            // Lancer le minuteur pour la durée minimale du splash screen
            Executors.newSingleThreadScheduledExecutor().schedule({
                minSplashDurationComplete = true
                tryToNavigateNext()
            }, MIN_SPLASH_DURATION_MS, TimeUnit.MILLISECONDS)
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

    // Nouvelle méthode pour vérifier les mises à jour avec callback
    private fun checkForUpdates() {
        try {
            val rootView = findViewById<View>(android.R.id.content)
            UpdateManager.checkForUpdate(this, rootView, false, this)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la vérification des mises à jour: ${e.message ?: "Message non disponible"}")
            // En cas d'erreur, on considère que la vérification est terminée
            updateCheckComplete = true
            tryToNavigateNext()
        }
    }

    // Callbacks de l'interface UpdateCallback
    override fun onUpdateCheckComplete() {
        updateCheckComplete = true
        // Si aucune mise à jour n'est disponible, on peut continuer
        tryToNavigateNext()
    }

    override fun onUpdateAvailable() {
        // Une mise à jour est disponible et affichée à l'utilisateur
        updateDialogShowing = true
        // On ne fait rien d'autre ici, car on attend que l'utilisateur prenne une décision
        CrashlyticsManager.log("Dialogue de mise à jour affiché à l'utilisateur")
    }

    override fun onUpdateProcessed(updateAccepted: Boolean) {
        // L'utilisateur a pris une décision concernant la mise à jour
        updateCheckComplete = true
        updateDialogShowing = false

        // Enregistrer l'action dans Analytics
        try {
            AnalyticsManager.logUserAction(
                "update_decision",
                "update",
                mapOf("update_accepted" to updateAccepted.toString())
            )
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'enregistrement de la décision de mise à jour: ${e.message ?: "Message non disponible"}")
        }

        // Naviguer vers l'écran suivant si la durée minimale du splash screen est écoulée
        tryToNavigateNext()
    }

    /**
     * Tente de naviguer vers l'écran suivant si toutes les conditions sont remplies.
     */
    private fun tryToNavigateNext() {
        // On ne navigue vers l'écran suivant que si:
        // 1. La vérification de mise à jour est terminée
        // 2. Aucun dialogue de mise à jour n'est actuellement affiché
        // 3. La durée minimale du splash screen est écoulée
        // 4. L'activité est toujours active
        if (updateCheckComplete && !updateDialogShowing && minSplashDurationComplete && !isFinishing && !isDestroyed) {
            redirectToMainScreen()
        } else {
            // Log pour le débogage
            if (!updateCheckComplete) {
                CrashlyticsManager.log("Navigation retardée: vérification de mise à jour en cours")
            } else if (updateDialogShowing) {
                CrashlyticsManager.log("Navigation retardée: dialogue de mise à jour affiché")
            } else if (!minSplashDurationComplete) {
                CrashlyticsManager.log("Navigation retardée: durée minimale du splash screen non atteinte")
            } else {
                CrashlyticsManager.log("Navigation retardée: activité en cours de fermeture")
            }
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

    private fun displayVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionText = findViewById<TextView>(R.id.shoopt_version_TV)
            versionText?.text = "v${versionName}"
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage de la version: ${e.message ?: "Message non disponible"}")
        }
    }

    private fun redirectToMainScreen() {
        try {
            // Double vérification pour s'assurer qu'aucun dialogue de mise à jour n'est affiché
            if (updateDialogShowing) {
                CrashlyticsManager.log("Tentative de redirection bloquée: dialogue de mise à jour toujours affiché")
                return
            }

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

            // Vérifier si le consentement analytics a déjà été demandé lors d'un précédent lancement
            // et si on a déjà la réponse de l'utilisateur dans les préférences stockées
            val hasConsentBeenRequested = UserPreferences.isAnalyticsConsentRequested(this)

            // Vérification et affichage du dialogue de consentement analytics si nécessaire
            if (!hasConsentBeenRequested && !analyticsConsentHandled) {
                showAnalyticsConsentDialog()
            } else {
                // Si le consentement a déjà été géré, on peut directement démarrer l'activité cible
                startTargetActivity(targetActivity)
            }
        } catch (e: Exception) {
            // Capture des erreurs lors de la redirection
            CrashlyticsManager.log("Erreur lors de la redirection vers l'écran principal: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "redirect_main_screen")
            CrashlyticsManager.logException(e)

            // Tentative de récupération en fermant simplement l'activité
            finish()
        }
    }

    private fun showAnalyticsConsentDialog() {
        try {
            // Afficher le dialogue de consentement analytics
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            val dialogFragment = AnalyticsConsentDialogFragment.newInstance()
            dialogFragment.setTargetFragment(null, 0) // Pas de cible spécifique
            dialogFragment.show(transaction, "analytics_consent_dialog")

            // Mettre à jour l'état du consentement analytics
            analyticsConsentHandled = true
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de consentement analytics: ${e.message ?: "Message non disponible"}")
            // En cas d'erreur, on continue quand même
            // (le consentement sera peut-être redemandé plus tard)
        }
    }

    private fun startTargetActivity(targetActivity: Class<*>) {
        // Transition directe sans délai supplémentaire car nous avons déjà attendu
        // que l'utilisateur prenne une décision concernant la mise à jour
        val intent = Intent(this, targetActivity)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun navigateToNextScreen() {
        // La vérification des notifications se fera dans MainActivity et non ici
        // pour éviter que l'utilisateur ne soit bloqué au SplashScreen
        if (FirebaseAuth.getInstance().currentUser != null) {
            // L'utilisateur est déjà connecté, on le redirige vers le MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // L'utilisateur n'est pas connecté, on le redirige vers le LoginActivity
            val preferences = UserPreferences.getInstance(this)
            if (UserPreferences.isFirstLaunch(this)) {
                // Si c'est la première utilisation, on redirige vers l'onboarding
                startActivity(Intent(this, OnboardingActivity::class.java))
                UserPreferences.setFirstLaunch(this, false)
            } else {
                // Sinon vers la page de login
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        finish() // On termine l'activité pour qu'elle ne reste pas en arrière-plan
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UpdateManager.UPDATE_REQUEST_CODE) {
            // Rétablir l'état du dialogue de mise à jour
            updateDialogShowing = false

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

            // Essayer de naviguer après la gestion du résultat
            updateCheckComplete = true
            tryToNavigateNext()
        }
    }

    // Gestion des résultats du dialogue de consentement analytics
    override fun onConsentGiven() {
        // L'utilisateur a donné son consentement pour le suivi analytics
        CrashlyticsManager.log("Consentement analytics accordé par l'utilisateur")

        // On détermine l'activité cible et on lance la navigation
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isOnboardingCompleted = UserPreferences.isOnboardingCompleted(this)

        val targetActivity = when {
            !isOnboardingCompleted -> OnboardingActivity::class.java
            currentUser != null -> MainActivity::class.java
            else -> LoginActivity::class.java
        }

        // Lancer la navigation vers l'écran cible
        startTargetActivity(targetActivity)
    }

    override fun onConsentDenied() {
        // L'utilisateur a refusé le consentement pour le suivi analytics
        CrashlyticsManager.log("Consentement analytics refusé par l'utilisateur")

        // On détermine l'activité cible et on lance la navigation
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isOnboardingCompleted = UserPreferences.isOnboardingCompleted(this)

        val targetActivity = when {
            !isOnboardingCompleted -> OnboardingActivity::class.java
            currentUser != null -> MainActivity::class.java
            else -> LoginActivity::class.java
        }

        // Lancer la navigation vers l'écran cible
        startTargetActivity(targetActivity)
    }
}
