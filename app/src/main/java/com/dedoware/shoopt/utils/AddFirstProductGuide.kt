package com.dedoware.shoopt.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.DrawableRes
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ui.components.SpotlightView

/**
 * Guide spécialisé pour accompagner les nouveaux utilisateurs dans l'ajout de leur premier produit.
 * Ce guide suit l'utilisateur à travers les différentes étapes du processus d'ajout de produit,
 * en expliquant chaque étape avec des tooltips et des spotlights.
 */
class AddFirstProductGuide(private val activity: Activity) {

    // Gestionnaire de guides pour créer les spotlights et tooltips
    private val guideManager = UserGuideManager(activity)

    // Handler pour différer l'affichage du guide
    private val handler = Handler(Looper.getMainLooper())

    // État actuel du guide
    private var currentState = GuideState.NOT_STARTED

    // Identifiants pour suivre l'état du guide entre les écrans
    companion object {
        const val GUIDE_ID = UserGuideManager.GUIDE_ADD_FIRST_PRODUCT
        const val PREF_GUIDE_STATE = "pref_first_product_guide_state"
        const val PREF_ONBOARDING_COMPLETED = "pref_onboarding_completed"

        // Temps d'attente avant de démarrer le guide (en millisecondes)
        private const val START_DELAY = 2000L
    }

    /**
     * États possibles du guide
     */
    enum class GuideState {
        NOT_STARTED,
        WELCOME_SHOWN,
        MAIN_SCREEN_ADD_BUTTON,
        PRODUCT_CHOICE_SCREEN,
        BARCODE_SCANNER_SCREEN,
        PRODUCT_FORM_BARCODE_FILLED,
        PRODUCT_FORM_PHOTO_BUTTON,
        PRODUCT_FORM_PHOTO_WARNING,
        PRODUCT_FORM_FIELDS_AUTOFILLED,
        PRODUCT_FORM_SAVE_BUTTON,
        MAIN_SCREEN_PRODUCT_ADDED,
        MAIN_SCREEN_ANALYZE_BUTTON,
        COMPLETED
    }

    /**
     * Vérifie si le guide doit être démarré automatiquement après l'onboarding.
     */
    fun checkAndStartGuideAfterOnboarding() {
        val preferences = activity.getSharedPreferences(UserGuideManager.PREF_USER_GUIDES, Activity.MODE_PRIVATE)
        val onboardingCompleted = preferences.getBoolean(PREF_ONBOARDING_COMPLETED, false)
        val guideState = preferences.getInt(PREF_GUIDE_STATE, GuideState.NOT_STARTED.ordinal)

        // Si l'onboarding vient d'être terminé et le guide n'a pas encore commencé
        if (onboardingCompleted && guideState == GuideState.NOT_STARTED.ordinal) {
            // Démarrer le guide avec un délai pour laisser l'écran principal s'afficher
            handler.postDelayed({
                startWelcomeGuide()
            }, START_DELAY)
        }
    }

    /**
     * Marque l'onboarding comme terminé pour déclencher le guide plus tard.
     */
    fun markOnboardingCompleted() {
        val preferences = activity.getSharedPreferences(UserGuideManager.PREF_USER_GUIDES, Activity.MODE_PRIVATE)
        preferences.edit()
            .putBoolean(PREF_ONBOARDING_COMPLETED, true)
            .apply()
    }

    /**
     * Sauvegarde l'état actuel du guide pour le reprendre plus tard.
     */
    private fun saveGuideState(state: GuideState) {
        currentState = state
        val preferences = activity.getSharedPreferences(UserGuideManager.PREF_USER_GUIDES, Activity.MODE_PRIVATE)
        preferences.edit()
            .putInt(PREF_GUIDE_STATE, state.ordinal)
            .apply()
    }

    /**
     * Récupère l'état actuel du guide.
     */
    fun getCurrentGuideState(): GuideState {
        val preferences = activity.getSharedPreferences(UserGuideManager.PREF_USER_GUIDES, Activity.MODE_PRIVATE)
        val stateOrdinal = preferences.getInt(PREF_GUIDE_STATE, GuideState.NOT_STARTED.ordinal)
        return GuideState.values()[stateOrdinal]
    }

    /**
     * Réinitialise l'état du guide pour le refaire depuis le début.
     */
    fun resetGuide() {
        val preferences = activity.getSharedPreferences(UserGuideManager.PREF_USER_GUIDES, Activity.MODE_PRIVATE)
        preferences.edit()
            .putInt(PREF_GUIDE_STATE, GuideState.NOT_STARTED.ordinal)
            .putBoolean(GUIDE_ID, false)
            .apply()
        currentState = GuideState.NOT_STARTED
    }

    /**
     * Marque le guide comme terminé.
     */
    fun completeGuide() {
        saveGuideState(GuideState.COMPLETED)
    }

    /**
     * Démarre le guide de bienvenue initial et enchaîne automatiquement sur l'étape suivante si un callback est fourni.
     * @param onWelcomeComplete Callback appelé après la confirmation du message de bienvenue (ex: pour afficher l'étape suivante)
     */
    fun startWelcomeGuide(onWelcomeComplete: (() -> Unit)? = null) {
        saveGuideState(GuideState.WELCOME_SHOWN)

        // Trouver la racine de l'activité pour le message de bienvenue
        val rootView = activity.findViewById<View>(android.R.id.content)

        guideManager.initGuide(GUIDE_ID, false)
            .addStep(
                rootView,
                R.string.guide_first_product_welcome_title,
                R.string.guide_first_product_welcome_desc,
                R.drawable.ic_launcher_foreground,
                SpotlightView.Shape.NONE, // Utilise une forme NONE pour désactiver le spotlight
                0,
                SpotlightView.TooltipPosition.AUTO
            )
            .setOnCompleteListener {
                // Passer à l'étape suivante après le message de bienvenue
                saveGuideState(GuideState.MAIN_SCREEN_ADD_BUTTON)
                onWelcomeComplete?.invoke()
            }
            .start()
    }

    /**
     * Affiche le guide sur le bouton d'ajout de produit dans l'écran principal.
     */
    fun showAddProductButtonGuide(addProductButton: View) {
        if (getCurrentGuideState() != GuideState.MAIN_SCREEN_ADD_BUTTON) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                addProductButton,
                R.string.guide_add_product_button_title,
                R.string.guide_add_product_button_desc,
                R.drawable.ic_add_circle,
                SpotlightView.Shape.CIRCLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour l'écran de choix de produit
                saveGuideState(GuideState.PRODUCT_CHOICE_SCREEN)
            }
            .start()
    }

    /**
     * Affiche le guide sur l'écran de choix de méthode d'ajout de produit.
     */
    fun showProductChoiceGuide(scanBarcodeButton: View, manualEntryButton: View) {
        if (getCurrentGuideState() != GuideState.PRODUCT_CHOICE_SCREEN) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                scanBarcodeButton,
                R.string.guide_product_choice_title,
                R.string.guide_product_choice_desc,
                R.drawable.ic_choice,
                SpotlightView.Shape.RECTANGLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .addStep(
                scanBarcodeButton,
                R.string.guide_barcode_scan_title,
                R.string.guide_barcode_scan_desc,
                R.drawable.ic_barcode_scan,
                SpotlightView.Shape.RECTANGLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour le scanner de code-barres
                saveGuideState(GuideState.BARCODE_SCANNER_SCREEN)
            }
            .start()
    }

    /**
     * Affiche le guide dans l'écran du scanner de code-barres.
     * Note: Cette étape peut être passive car l'utilisateur doit scanner un code-barres.
     */
    fun showBarcodeScannerGuide(scannerView: View) {
        if (getCurrentGuideState() != GuideState.BARCODE_SCANNER_SCREEN) return

        // Lors de l'entrée dans l'écran de scan, nous prévoyons déjà l'état suivant
        // car nous ne montrons pas de spotlight dans cet écran
        saveGuideState(GuideState.PRODUCT_FORM_BARCODE_FILLED)
    }

    /**
     * Affiche le guide sur le champ du code-barres déjà rempli.
     */
    fun showBarcodeFilledGuide(barcodeField: View) {
        if (getCurrentGuideState() != GuideState.PRODUCT_FORM_BARCODE_FILLED) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                barcodeField,
                R.string.guide_barcode_filled_title,
                R.string.guide_barcode_filled_desc,
                R.drawable.ic_barcode_check,
                SpotlightView.Shape.RECTANGLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour le bouton de photo
                saveGuideState(GuideState.PRODUCT_FORM_PHOTO_BUTTON)
            }
            .start()
    }

    /**
     * Affiche le guide sur le bouton de prise de photo.
     */
    fun showTakePhotoButtonGuide(photoButton: View) {
        if (getCurrentGuideState() != GuideState.PRODUCT_FORM_PHOTO_BUTTON) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                photoButton,
                R.string.guide_take_photo_button_title,
                R.string.guide_take_photo_button_desc,
                R.drawable.ic_camera,
                SpotlightView.Shape.CIRCLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour l'avertissement sur la photo
                saveGuideState(GuideState.PRODUCT_FORM_PHOTO_WARNING)
            }
            .start()
    }

    /**
     * Affiche l'avertissement sur la prise de photo.
     */
    fun showPhotoWarningGuide(photoPreview: View) {
        if (getCurrentGuideState() != GuideState.PRODUCT_FORM_PHOTO_WARNING) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                photoPreview,
                R.string.guide_photo_warning_title,
                R.string.guide_photo_warning_desc,
                R.drawable.ic_warning,
                SpotlightView.Shape.RECTANGLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour les champs auto-remplis
                saveGuideState(GuideState.PRODUCT_FORM_FIELDS_AUTOFILLED)
            }
            .start()
    }

    /**
     * Affiche le guide sur les champs automatiquement remplis.
     */
    fun showFieldsAutofilledGuide(formLayout: View) {
        if (getCurrentGuideState() != GuideState.PRODUCT_FORM_FIELDS_AUTOFILLED) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                formLayout,
                R.string.guide_fields_autofilled_title,
                R.string.guide_fields_autofilled_desc,
                R.drawable.ic_magic_wand,
                SpotlightView.Shape.RECTANGLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour le bouton de sauvegarde
                saveGuideState(GuideState.PRODUCT_FORM_SAVE_BUTTON)
            }
            .start()
    }

    /**
     * Affiche le guide sur le bouton de sauvegarde du produit.
     */
    fun showSaveProductButtonGuide(saveButton: View) {
        if (getCurrentGuideState() != GuideState.PRODUCT_FORM_SAVE_BUTTON) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                saveButton,
                R.string.guide_save_product_title,
                R.string.guide_save_product_desc,
                R.drawable.ic_save,
                SpotlightView.Shape.CIRCLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Préparer pour le message de confirmation d'ajout
                saveGuideState(GuideState.MAIN_SCREEN_PRODUCT_ADDED)
            }
            .start()
    }

    /**
     * Affiche le guide de confirmation d'ajout du produit.
     */
    fun showProductAddedGuide(rootView: View) {
        if (getCurrentGuideState() != GuideState.MAIN_SCREEN_PRODUCT_ADDED) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                rootView,
                R.string.guide_product_added_title,
                R.string.guide_product_added_desc,
                R.drawable.ic_check_circle,
                SpotlightView.Shape.CIRCLE,
                0,
                SpotlightView.TooltipPosition.AUTO
            )
            .setOnCompleteListener {
                // Préparer pour le bouton d'analyse
                saveGuideState(GuideState.MAIN_SCREEN_ANALYZE_BUTTON)
            }
            .start()
    }

    /**
     * Affiche le guide sur le bouton d'analyse.
     */
    fun showAnalyzeButtonGuide(analyzeButton: View) {
        if (getCurrentGuideState() != GuideState.MAIN_SCREEN_ANALYZE_BUTTON) return

        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                analyzeButton,
                R.string.guide_analyze_button_title,
                R.string.guide_analyze_button_desc,
                R.drawable.ic_analytics,
                SpotlightView.Shape.CIRCLE,
                8,
                SpotlightView.TooltipPosition.BOTTOM
            )
            .setOnCompleteListener {
                // Terminer avec un message de félicitation
                showCompletionGuide(analyzeButton)
            }
            .start()
    }

    /**
     * Affiche le message final de félicitation.
     */
    private fun showCompletionGuide(rootView: View) {
        guideManager.initGuide(GUIDE_ID, true)
            .addStep(
                rootView,
                R.string.guide_completion_title,
                R.string.guide_completion_desc,
                R.drawable.ic_celebration,
                SpotlightView.Shape.CIRCLE,
                0,
                SpotlightView.TooltipPosition.AUTO
            )
            .setOnCompleteListener {
                // Guide terminé
                completeGuide()
            }
            .start()
    }

    /**
     * Vérifie si le guide a été complété.
     */
    fun isGuideCompleted(): Boolean {
        return getCurrentGuideState() == GuideState.COMPLETED
    }
}
