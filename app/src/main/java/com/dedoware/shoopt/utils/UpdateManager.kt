package com.dedoware.shoopt.utils

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dedoware.shoopt.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.util.concurrent.TimeUnit

/**
 * Interface pour les callbacks de mise à jour
 */
interface UpdateCallback {
    /**
     * Appelé lorsqu'aucune mise à jour n'est disponible ou que la vérification est terminée
     */
    fun onUpdateCheckComplete()

    /**
     * Appelé lorsqu'une mise à jour est disponible et affichée à l'utilisateur
     */
    fun onUpdateAvailable()

    /**
     * Appelé lorsqu'une mise à jour a été acceptée, différée ou refusée
     * @param updateAccepted true si l'utilisateur a accepté la mise à jour
     */
    fun onUpdateProcessed(updateAccepted: Boolean)
}

/**
 * Gestionnaire de mises à jour de l'application.
 * Cette classe utilise l'API In-App Update de Google Play pour vérifier et installer les mises à jour.
 */
object UpdateManager : DefaultLifecycleObserver {
    const val UPDATE_REQUEST_CODE = 500
    private const val PREF_NAME = "update_manager_prefs"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_POSTPONED_UPDATE = "postponed_update"
    private const val KEY_POSTPONED_VERSION = "postponed_version"
    private const val KEY_POSTPONED_TIMESTAMP = "postponed_timestamp"
    // Changé de const val à val car TimeUnit.toMillis() n'est pas une constante de compilation
    private val UPDATE_CHECK_INTERVAL = TimeUnit.HOURS.toMillis(12) // 12 heures entre les vérifications
    private val POSTPONE_INTERVAL = TimeUnit.DAYS.toMillis(3) // 3 jours avant de reproposer une mise à jour différée

    private var appUpdateManager: AppUpdateManager? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null
    private var updateDialog: AlertDialog? = null
    private var snackbar: Snackbar? = null
    private var updateAvailable = false
    private var currentActivity: AppCompatActivity? = null
    private var updateInfo: AppUpdateInfo? = null
    private var mainHandler: Handler? = null
    private var currentUpdateCallback: UpdateCallback? = null

    /**
     * Initialise le gestionnaire de mises à jour avec une activité.
     * Doit être appelé dans onCreate() de l'activité principale.
     */
    fun init(activity: AppCompatActivity) {
        currentActivity = activity
        mainHandler = Handler(Looper.getMainLooper())

        // Ajoute ce gestionnaire comme observateur du cycle de vie de l'activité
        activity.lifecycle.addObserver(this)

        // Initialise le gestionnaire de mise à jour
        try {
            appUpdateManager = AppUpdateManagerFactory.create(activity)
        } catch (e: Exception) {
            CrashlyticsManager.log("Exception lors de l'initialisation du gestionnaire de mises à jour: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("update_manager_init_error", e.javaClass.name)
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Vérifie si une mise à jour est disponible et la propose à l'utilisateur
     * selon son importance et son ancienneté.
     */
    fun checkForUpdate(activity: AppCompatActivity, rootView: View? = null, forceCheck: Boolean = false, callback: UpdateCallback? = null) {
        if (activity.isFinishing || activity.isDestroyed) {
            callback?.onUpdateCheckComplete()
            return
        }

        // Enregistrement du callback
        currentUpdateCallback = callback

        // Mise à jour de l'activité courante
        currentActivity = activity

        // Vérifie si on doit faire une vérification maintenant
        val sharedPrefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastCheckTime = sharedPrefs.getLong(KEY_LAST_UPDATE_CHECK, 0)
        val currentTime = System.currentTimeMillis()

        // Si la dernière vérification est récente et qu'on ne force pas la vérification, on vérifie les mises à jour différées
        if (!forceCheck && currentTime - lastCheckTime < UPDATE_CHECK_INTERVAL) {
            checkForPostponedUpdates(activity, rootView)
            callback?.onUpdateCheckComplete()
            return
        }

        try {
            if (appUpdateManager == null) {
                appUpdateManager = AppUpdateManagerFactory.create(activity)
            }

            // Enregistre le moment de la vérification
            sharedPrefs.edit().putLong(KEY_LAST_UPDATE_CHECK, currentTime).apply()

            val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                val bundle = Bundle()
                bundle.putString("update_availability", appUpdateInfo.updateAvailability().toString())
                bundle.putString("available_version_code", (appUpdateInfo.availableVersionCode() ?: -1).toString())
                AnalyticsManager.logEvent("update_check", bundle)

                // Stocke l'info pour une utilisation ultérieure
                updateInfo = appUpdateInfo
                updateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

                if (updateAvailable) {
                    // Vérifie si cette mise à jour a été différée et si le délai de report est écoulé
                    val postponedVersion = sharedPrefs.getInt(KEY_POSTPONED_VERSION, -1)
                    val postponedTimestamp = sharedPrefs.getLong(KEY_POSTPONED_TIMESTAMP, 0)
                    val isPostponed = postponedVersion == appUpdateInfo.availableVersionCode() &&
                                     currentTime - postponedTimestamp < POSTPONE_INTERVAL

                    if (!isPostponed) {
                        // Proposer la mise à jour dès qu'elle est disponible, sans attendre un délai
                        if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                            // Mise à jour flexible (non bloquante)
                            showUpdateDialog(activity, appUpdateInfo, AppUpdateType.FLEXIBLE, rootView)
                            callback?.onUpdateAvailable()
                            return@addOnSuccessListener
                        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            // Mise à jour immédiate (bloquante)
                            showUpdateDialog(activity, appUpdateInfo, AppUpdateType.IMMEDIATE, rootView)
                            callback?.onUpdateAvailable()
                            return@addOnSuccessListener
                        }
                    }
                }
                // Aucune mise à jour disponible ou mise à jour différée
                callback?.onUpdateCheckComplete()
            }.addOnFailureListener { e ->
                CrashlyticsManager.log("Erreur lors de la vérification des mises à jour: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.logException(e)
                callback?.onUpdateCheckComplete()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Exception lors de la vérification des mises à jour: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("update_check_error", e.javaClass.name)
            CrashlyticsManager.logException(e)
            callback?.onUpdateCheckComplete()
        }
    }

    /**
     * Vérifie s'il y a des mises à jour différées qui devraient être à nouveau proposées.
     */
    private fun checkForPostponedUpdates(activity: AppCompatActivity, rootView: View? = null) {
        val sharedPrefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isPostponed = sharedPrefs.getBoolean(KEY_POSTPONED_UPDATE, false)

        if (isPostponed) {
            val postponedTimestamp = sharedPrefs.getLong(KEY_POSTPONED_TIMESTAMP, 0)
            val currentTime = System.currentTimeMillis()

            // Si le délai de report est écoulé, on vérifie à nouveau les mises à jour
            if (currentTime - postponedTimestamp > POSTPONE_INTERVAL) {
                // Réinitialiser l'état différé
                sharedPrefs.edit().putBoolean(KEY_POSTPONED_UPDATE, false).apply()
                // Forcer une vérification de mise à jour
                checkForUpdate(activity, rootView, true)
            }
        }
    }

    /**
     * Affiche un dialogue personnalisé pour proposer la mise à jour à l'utilisateur.
     */
    private fun showUpdateDialog(activity: AppCompatActivity, appUpdateInfo: AppUpdateInfo, updateType: Int, rootView: View? = null) {
        try {
            // Vérifier si l'activité est toujours active
            if (activity.isFinishing || activity.isDestroyed) {
                return
            }

            // Fermer le dialogue existant s'il est affiché
            dismissUpdateDialog()

            // Fermer le Snackbar existant s'il est affiché
            dismissSnackbar()

            val dialogView = LayoutInflater.from(activity).inflate(R.layout.update_dialog, null)
            val builder = AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(updateType == AppUpdateType.FLEXIBLE)

            updateDialog = builder.create()
            updateDialog?.setOnDismissListener {
                // Ne faire rien ici, car le dialogue peut être fermé par plusieurs chemins différents
            }
            updateDialog?.show()

            // Configurer les boutons
            val updateNowButton = dialogView.findViewById<MaterialButton>(R.id.btn_update_now)
            val laterButton = dialogView.findViewById<MaterialButton>(R.id.btn_later)

            updateNowButton.setOnClickListener {
                if (updateType == AppUpdateType.FLEXIBLE) {
                    initiateFlexibleUpdate(activity, appUpdateInfo, rootView)
                } else {
                    initiateImmediateUpdate(activity, appUpdateInfo)
                }
                currentUpdateCallback?.onUpdateProcessed(true)
                // Ne pas fermer le dialogue immédiatement, attendre la confirmation du lancement de la mise à jour
            }

            if (updateType == AppUpdateType.FLEXIBLE) {
                laterButton.setOnClickListener {
                    postponeUpdate(activity, appUpdateInfo)
                    updateDialog?.dismiss()
                    currentUpdateCallback?.onUpdateProcessed(false)
                }
            } else {
                // Pour les mises à jour immédiates, permettre de reporter mais avertir que c'est important
                laterButton.text = activity.getString(R.string.update_later_critical)
                laterButton.setOnClickListener {
                    postponeUpdate(activity, appUpdateInfo)
                    updateDialog?.dismiss()
                    currentUpdateCallback?.onUpdateProcessed(false)

                    // Afficher un Snackbar pour rappeler à l'utilisateur l'importance de la mise à jour
                    showImportantUpdateSnackbar(activity, rootView)
                }
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de mise à jour: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
            currentUpdateCallback?.onUpdateCheckComplete()
        }
    }

    /**
     * Enregistre qu'une mise à jour a été différée par l'utilisateur.
     */
    private fun postponeUpdate(activity: AppCompatActivity, appUpdateInfo: AppUpdateInfo) {
        val sharedPrefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean(KEY_POSTPONED_UPDATE, true)
            putInt(KEY_POSTPONED_VERSION, appUpdateInfo.availableVersionCode() ?: -1)
            putLong(KEY_POSTPONED_TIMESTAMP, System.currentTimeMillis())
            apply()
        }

        val bundle = Bundle()
        bundle.putString("version_code", (appUpdateInfo.availableVersionCode() ?: -1).toString())
        bundle.putString("update_type", if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) "immediate" else "flexible")
        AnalyticsManager.logEvent("update_postponed", bundle)
    }

    /**
     * Affiche un Snackbar pour rappeler à l'utilisateur l'importance d'une mise à jour critique.
     */
    private fun showImportantUpdateSnackbar(activity: Activity, rootView: View? = null) {
        val snackbarView = rootView ?: activity.findViewById(android.R.id.content)

        snackbar = Snackbar.make(
            snackbarView,
            activity.getString(R.string.update_important_reminder),
            Snackbar.LENGTH_LONG
        ).apply {
            setAction(activity.getString(R.string.update_now)) {
                // Si l'information de mise à jour est disponible, relancer le dialogue
                updateInfo?.let { info ->
                    if (currentActivity != null && !currentActivity!!.isFinishing) {
                        showUpdateDialog(currentActivity!!, info, AppUpdateType.IMMEDIATE, rootView)
                    }
                }
            }
            show()
        }
    }

    /**
     * Lance une mise à jour immédiate qui bloque l'utilisateur jusqu'à ce que la mise à jour soit terminée.
     */
    private fun initiateImmediateUpdate(activity: AppCompatActivity, appUpdateInfo: AppUpdateInfo) {
        try {
            val bundle = Bundle()
            bundle.putString("version_code", (appUpdateInfo.availableVersionCode() ?: -1).toString())
            bundle.putString("staleness_days", (appUpdateInfo.clientVersionStalenessDays() ?: -1).toString())
            AnalyticsManager.logEvent("update_immediate_initiated", bundle)

            // Ferme le dialogue après confirmation que la mise à jour va démarrer
            updateDialog?.dismiss()

            appUpdateManager?.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            CrashlyticsManager.log("Erreur lors du démarrage du flux de mise à jour immédiate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)

            // Afficher un message d'erreur à l'utilisateur
            showUpdateErrorSnackbar(activity)
        }
    }

    /**
     * Lance une mise à jour flexible qui permet à l'utilisateur de continuer à utiliser l'application
     * pendant le téléchargement de la mise à jour.
     */
    private fun initiateFlexibleUpdate(activity: AppCompatActivity, appUpdateInfo: AppUpdateInfo, rootView: View?) {
        try {
            val bundle = Bundle()
            bundle.putString("version_code", (appUpdateInfo.availableVersionCode() ?: -1).toString())
            bundle.putString("staleness_days", (appUpdateInfo.clientVersionStalenessDays() ?: -1).toString())
            AnalyticsManager.logEvent("update_flexible_initiated", bundle)

            // Configurer le listener pour suivre l'état d'installation
            setupInstallStateListener(activity, rootView)

            // Ferme le dialogue après confirmation que la mise à jour va démarrer
            updateDialog?.dismiss()

            // Affiche un Snackbar pour informer que le téléchargement commence
            showDownloadStartedSnackbar(activity, rootView)

            appUpdateManager?.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            CrashlyticsManager.log("Erreur lors du démarrage du flux de mise à jour flexible: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)

            // Afficher un message d'erreur à l'utilisateur
            showUpdateErrorSnackbar(activity)
        }
    }

    /**
     * Configure le listener d'état d'installation pour les mises à jour flexibles.
     */
    private fun setupInstallStateListener(activity: AppCompatActivity, rootView: View?) {
        try {
            // Supprimer l'ancien listener s'il existe
            removeInstallStateUpdateListener()

            // Configurer le nouveau listener
            installStateUpdatedListener = InstallStateUpdatedListener { state ->
                when (state.installStatus()) {
                    InstallStatus.DOWNLOADING -> {
                        // La mise à jour est en cours de téléchargement
                        // On pourrait ajouter une barre de progression ici
                        val bytesDownloaded = state.bytesDownloaded()
                        val totalBytesToDownload = state.totalBytesToDownload()

                        // Log pour le débogage
                        if (totalBytesToDownload > 0) {
                            val progress = (bytesDownloaded * 100 / totalBytesToDownload).toInt()
                            if (progress % 20 == 0) { // Loguer tous les 20%
                                val progressBundle = Bundle()
                                progressBundle.putInt("progress", progress)
                                AnalyticsManager.logEvent("update_download_progress", progressBundle)
                            }
                        }
                    }
                    InstallStatus.DOWNLOADED -> {
                        // La mise à jour a été téléchargée, informer l'utilisateur
                        dismissSnackbar() // Fermer le snackbar précédent s'il existe
                        popupSnackbarForCompleteUpdate(activity, rootView)
                        AnalyticsManager.logEvent("update_downloaded", Bundle())
                    }
                    InstallStatus.INSTALLED -> {
                        AnalyticsManager.logEvent("update_installed", Bundle())
                        dismissSnackbar()
                        removeInstallStateUpdateListener()
                    }
                    InstallStatus.FAILED -> {
                        val errorBundle = Bundle()
                        errorBundle.putString("error_code", state.installErrorCode().toString())
                        AnalyticsManager.logEvent("update_failed", errorBundle)

                        dismissSnackbar()
                        showUpdateErrorSnackbar(activity)
                        removeInstallStateUpdateListener()
                    }
                    InstallStatus.CANCELED -> {
                        AnalyticsManager.logEvent("update_canceled", Bundle())
                        dismissSnackbar()
                        removeInstallStateUpdateListener()
                    }
                }
            }

            appUpdateManager?.registerListener(installStateUpdatedListener!!)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du listener d'état d'installation: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Affiche une notification Snackbar pour informer que le téléchargement de la mise à jour a commencé.
     */
    private fun showDownloadStartedSnackbar(activity: Activity, rootView: View?) {
        val snackbarView = rootView ?: activity.findViewById(android.R.id.content)

        snackbar = Snackbar.make(
            snackbarView,
            activity.getString(R.string.update_downloading),
            Snackbar.LENGTH_LONG
        ).apply {
            show()
        }
    }

    /**
     * Affiche une notification Snackbar pour informer l'utilisateur d'une erreur lors de la mise à jour.
     */
    private fun showUpdateErrorSnackbar(activity: Activity) {
        try {
            val snackbarView = activity.findViewById<View>(android.R.id.content)

            snackbar = Snackbar.make(
                snackbarView,
                activity.getString(R.string.update_error),
                Snackbar.LENGTH_LONG
            ).apply {
                setAction(activity.getString(R.string.retry)) {
                    // Tenter de vérifier à nouveau les mises à jour
                    if (currentActivity != null && !currentActivity!!.isFinishing) {
                        checkForUpdate(currentActivity!!, null, true)
                    }
                }
                show()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du snackbar d'erreur: ${e.message ?: "Message non disponible"}")
        }
    }

    /**
     * Affiche une notification Snackbar pour informer l'utilisateur que la mise à jour a été téléchargée
     * et qu'il peut l'installer.
     */
    private fun popupSnackbarForCompleteUpdate(activity: Activity, rootView: View?) {
        try {
            val snackbarView = rootView ?: activity.findViewById(android.R.id.content)

            // Fermer l'ancien Snackbar s'il existe
            dismissSnackbar()

            snackbar = Snackbar.make(
                snackbarView,
                activity.getString(R.string.update_downloaded),
                Snackbar.LENGTH_INDEFINITE
            ).apply {
                setAction(activity.getString(R.string.install)) {
                    appUpdateManager?.completeUpdate()
                    AnalyticsManager.logEvent("update_install_clicked", Bundle())
                }
                show()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du snackbar de mise à jour complète: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Ferme le Snackbar actuel s'il est affiché.
     */
    private fun dismissSnackbar() {
        snackbar?.dismiss()
        snackbar = null
    }

    /**
     * À appeler dans onResume() de l'activité pour vérifier si une mise à jour interrompue est en attente.
     */
    fun checkForPendingUpdate(activity: AppCompatActivity, rootView: View? = null) {
        currentActivity = activity

        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            // Stocke l'information pour une utilisation ultérieure
            updateInfo = appUpdateInfo

            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                // Une mise à jour a été téléchargée mais pas encore installée
                popupSnackbarForCompleteUpdate(activity, rootView)
            } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // Une mise à jour immédiate était en cours mais a été interrompue
                try {
                    appUpdateManager?.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: IntentSender.SendIntentException) {
                    CrashlyticsManager.log("Erreur lors de la reprise de mise à jour immédiate: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.logException(e)
                    showUpdateErrorSnackbar(activity)
                }
            }
        }
    }

    // Méthodes observateurs du cycle de vie

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (owner is AppCompatActivity) {
            checkForPendingUpdate(owner)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Ne pas fermer les dialogues ici pour éviter les problèmes lors d'une rotation d'écran
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        if (owner is AppCompatActivity && owner == currentActivity) {
            onDestroy()
            currentActivity = null
        }
    }

    /**
     * À appeler dans onDestroy() de l'activité pour éviter les fuites de mémoire.
     */
    fun onDestroy() {
        removeInstallStateUpdateListener()
        dismissUpdateDialog()
        dismissSnackbar()
        mainHandler = null
        currentUpdateCallback = null
    }

    /**
     * Ferme le dialogue de mise à jour s'il est ouvert.
     */
    private fun dismissUpdateDialog() {
        try {
            if (updateDialog?.isShowing == true) {
                updateDialog?.dismiss()
            }
            updateDialog = null
        } catch (e: Exception) {
            // Ignore les exceptions lors de la fermeture du dialogue
        }
    }

    /**
     * Supprime l'écouteur d'état d'installation pour éviter les fuites de mémoire.
     */
    private fun removeInstallStateUpdateListener() {
        try {
            if (appUpdateManager != null && installStateUpdatedListener != null) {
                appUpdateManager?.unregisterListener(installStateUpdatedListener!!)
                installStateUpdatedListener = null
            }
        } catch (e: Exception) {
            // Ignore les exceptions lors de la suppression du listener
        }
    }
}
