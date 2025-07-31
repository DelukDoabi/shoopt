package com.dedoware.shoopt.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.dedoware.shoopt.R
import android.view.View

/**
 * Extensions pour faciliter l'intégration des guides utilisateur dans les activités.
 */

/**
 * Démarre le guide d'ajout de premier produit après l'onboarding.
 * Cette fonction devrait être appelée depuis l'activité principale une fois que l'onboarding est terminé.
 * @param addProductButton La référence au bouton d'ajout de produit qui sera mis en évidence.
 * @param analyzeButton La référence au bouton d'analyse qui sera mis en évidence à la fin du guide.
 */
fun Activity.startFirstProductGuideAfterOnboarding(addProductButton: View, analyzeButton: View) {
    val firstTimeManager = FirstTimeUserManager(this)
    val addProductGuide = AddFirstProductGuide(this)

    // Vérifier si l'onboarding vient d'être complété et si le guide n'a pas encore été montré
    if (firstTimeManager.shouldShowAddProductGuide()) {
        // Marquer l'onboarding comme complété dans le guide
        addProductGuide.markOnboardingCompleted()

        // Attendre 2 secondes avant de démarrer le guide pour laisser l'écran principal s'afficher
        Handler(Looper.getMainLooper()).postDelayed({
            // Démarrer le guide de bienvenue
            addProductGuide.startWelcomeGuide()

            // Une fois le message de bienvenue terminé, le guide passera automatiquement
            // à l'étape suivante (bouton d'ajout de produit)
            Handler(Looper.getMainLooper()).postDelayed({
                addProductGuide.showAddProductButtonGuide(addProductButton)
            }, 500) // Petit délai pour éviter que les étapes ne se chevauchent
        }, 2000) // 2 secondes de délai initial

        // Marquer que le guide a été montré
        firstTimeManager.markAddProductGuideShown()
    }
}

/**
 * Vérifie où en est le guide d'ajout de premier produit et affiche l'étape correspondante.
 * Cette fonction doit être appelée dans onResume() des activités concernées.
 * Elle détermine automatiquement quelle étape du guide afficher en fonction du contexte actuel.
 * @param views Map associant les états du guide aux vues correspondantes
 */
fun Activity.continueFirstProductGuideIfNeeded(views: Map<AddFirstProductGuide.GuideState, View>) {
    val addProductGuide = AddFirstProductGuide(this)
    val currentState = addProductGuide.getCurrentGuideState()

    // Si le guide est complété ou pas encore commencé, ne rien faire
    if (currentState == AddFirstProductGuide.GuideState.COMPLETED ||
        currentState == AddFirstProductGuide.GuideState.NOT_STARTED) {
        return
    }

    // Récupérer la vue correspondant à l'état actuel
    val targetView = views[currentState] ?: return

    // Afficher l'étape appropriée en fonction de l'état actuel
    when (currentState) {
        AddFirstProductGuide.GuideState.WELCOME_SHOWN -> {
            // Ne rien faire, cette étape est gérée par startFirstProductGuideAfterOnboarding
        }
        AddFirstProductGuide.GuideState.MAIN_SCREEN_ADD_BUTTON -> {
            addProductGuide.showAddProductButtonGuide(targetView)
        }
        AddFirstProductGuide.GuideState.PRODUCT_CHOICE_SCREEN -> {
            // On suppose que la vue cible est le bouton de scan de code-barres
            addProductGuide.showProductChoiceGuide(targetView, targetView)
        }
        AddFirstProductGuide.GuideState.BARCODE_SCANNER_SCREEN -> {
            addProductGuide.showBarcodeScannerGuide(targetView)
        }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_BARCODE_FILLED -> {
            addProductGuide.showBarcodeFilledGuide(targetView)
        }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_PHOTO_BUTTON -> {
            addProductGuide.showTakePhotoButtonGuide(targetView)
        }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_PHOTO_WARNING -> {
            addProductGuide.showPhotoWarningGuide(targetView)
        }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_FIELDS_AUTOFILLED -> {
            addProductGuide.showFieldsAutofilledGuide(targetView)
        }
        AddFirstProductGuide.GuideState.PRODUCT_FORM_SAVE_BUTTON -> {
            addProductGuide.showSaveProductButtonGuide(targetView)
        }
        AddFirstProductGuide.GuideState.MAIN_SCREEN_PRODUCT_ADDED -> {
            addProductGuide.showProductAddedGuide(targetView)
        }
        AddFirstProductGuide.GuideState.MAIN_SCREEN_ANALYZE_BUTTON -> {
            addProductGuide.showAnalyzeButtonGuide(targetView)
        }
        else -> {} // Ne rien faire pour les autres états
    }
}
