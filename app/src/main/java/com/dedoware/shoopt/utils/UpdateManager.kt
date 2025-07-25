package com.dedoware.shoopt.utils

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

/**
 * Gestionnaire de mises à jour de l'application.
 * Cette classe utilise l'API In-App Update de Google Play pour vérifier et installer les mises à jour.
 */
object UpdateManager {
    const val UPDATE_REQUEST_CODE = 500

    private var appUpdateManager: AppUpdateManager? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null
    private var updateDialog: AlertDialog? = null

    /**
     * Vérifie si une mise à jour est disponible et la propose à l'utilisateur
     * selon son importance et son ancienneté.
     */
    fun checkForUpdate(activity: AppCompatActivity, rootView: View? = null) {
        try {
            appUpdateManager = AppUpdateManagerFactory.create(activity)

            val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                val bundle = Bundle()
                bundle.putString("update_availability", appUpdateInfo.updateAvailability().toString())
                bundle.putString("available_version_code", (appUpdateInfo.availableVersionCode() ?: -1).toString())
                AnalyticsManager.logEvent("update_check", bundle)

                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    // Proposer la mise à jour dès qu'elle est disponible, sans attendre un délai
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        // Mise à jour flexible (non bloquante)
                        showUpdateDialog(activity, appUpdateInfo, AppUpdateType.FLEXIBLE)
                    } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        // Mise à jour immédiate (bloquante)
                        initiateImmediateUpdate(activity, appUpdateInfo)
                    }
                }
            }.addOnFailureListener { e ->
                CrashlyticsManager.log("Erreur lors de la vérification des mises à jour: ${e.message ?: "Message non disponible"}")
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Exception lors de l'initialisation du gestionnaire de mises à jour: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("update_manager_init_error", e.javaClass.name)
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Affiche un dialogue personnalisé pour proposer la mise à jour à l'utilisateur.
     */
    private fun showUpdateDialog(activity: AppCompatActivity, appUpdateInfo: AppUpdateInfo, updateType: Int) {
        try {
            // Fermer le dialogue existant s'il est affiché
            updateDialog?.dismiss()

            val dialogView = LayoutInflater.from(activity).inflate(R.layout.update_dialog, null)
            val builder = AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(updateType == AppUpdateType.FLEXIBLE)

            updateDialog = builder.create()
            updateDialog?.show()

            // Configurer les boutons
            val updateNowButton = dialogView.findViewById<MaterialButton>(R.id.btn_update_now)
            val laterButton = dialogView.findViewById<MaterialButton>(R.id.btn_later)

            updateNowButton.setOnClickListener {
                if (updateType == AppUpdateType.FLEXIBLE) {
                    initiateFlexibleUpdate(activity, appUpdateInfo, null)
                } else {
                    initiateImmediateUpdate(activity, appUpdateInfo)
                }
                updateDialog?.dismiss()
            }

            if (updateType == AppUpdateType.FLEXIBLE) {
                laterButton.setOnClickListener {
                    AnalyticsManager.logEvent("update_postponed", Bundle())
                    updateDialog?.dismiss()
                }
            } else {
                // Pour les mises à jour immédiates, ne pas permettre d'annuler
                laterButton.visibility = View.GONE
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de mise à jour: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
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

            appUpdateManager?.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            CrashlyticsManager.log("Erreur lors du démarrage du flux de mise à jour immédiate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
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
            installStateUpdatedListener = InstallStateUpdatedListener { state ->
                when (state.installStatus()) {
                    InstallStatus.DOWNLOADED -> {
                        // La mise à jour a été téléchargée, informer l'utilisateur
                        popupSnackbarForCompleteUpdate(activity, rootView)
                        AnalyticsManager.logEvent("update_downloaded", Bundle())
                    }
                    InstallStatus.INSTALLED -> {
                        AnalyticsManager.logEvent("update_installed", Bundle())
                        removeInstallStateUpdateListener()
                    }
                    InstallStatus.FAILED -> {
                        val errorBundle = Bundle()
                        errorBundle.putString("error_code", state.installErrorCode().toString())
                        AnalyticsManager.logEvent("update_failed", errorBundle)
                        removeInstallStateUpdateListener()
                    }
                }
            }

            appUpdateManager?.registerListener(installStateUpdatedListener!!)

            appUpdateManager?.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            CrashlyticsManager.log("Erreur lors du démarrage du flux de mise à jour flexible: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Affiche une notification Snackbar pour informer l'utilisateur que la mise à jour a été téléchargée
     * et qu'il peut l'installer.
     */
    private fun popupSnackbarForCompleteUpdate(activity: Activity, rootView: View?) {
        val snackbarView = rootView ?: activity.findViewById(android.R.id.content)

        Snackbar.make(
            snackbarView,
            activity.getString(R.string.update_downloaded),
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction(activity.getString(R.string.install)) { appUpdateManager?.completeUpdate() }
            show()
        }
    }

    /**
     * À appeler dans onResume() de l'activité pour vérifier si une mise à jour interrompue est en attente.
     */
    fun checkForPendingUpdate(activity: AppCompatActivity, rootView: View? = null) {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
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
                }
            }
        }
    }

    /**
     * À appeler dans onDestroy() de l'activité pour éviter les fuites de mémoire.
     */
    fun onDestroy() {
        removeInstallStateUpdateListener()
        dismissUpdateDialog()
    }

    /**
     * Ferme le dialogue de mise à jour s'il est ouvert.
     */
    private fun dismissUpdateDialog() {
        if (updateDialog?.isShowing == true) {
            updateDialog?.dismiss()
            updateDialog = null
        }
    }

    /**
     * Supprime l'écouteur d'état d'installation pour éviter les fuites de mémoire.
     */
    private fun removeInstallStateUpdateListener() {
        if (appUpdateManager != null && installStateUpdatedListener != null) {
            appUpdateManager?.unregisterListener(installStateUpdatedListener!!)
            installStateUpdatedListener = null
        }
    }
}
