package com.dedoware.shoopt.utils

import android.content.Context
import android.util.Log

/**
 * Utilitaire pour déboguer et tester la fonctionnalité de premier produit
 */
object FirstProductTestHelper {

    /**
     * Remet à zéro complètement le statut du premier produit
     * À utiliser UNIQUEMENT pour les tests
     */
    fun resetFirstProductStatus(context: Context) {
        try {
            val firstProductManager = FirstProductManager.getInstance(context)
            firstProductManager.resetAll()

            Log.d("SHOOPT_FIRST_PRODUCT", "=== RESET FORCE DU PREMIER PRODUIT ===")
            Log.d("SHOOPT_FIRST_PRODUCT", "Statut après reset:")
            Log.d("SHOOPT_FIRST_PRODUCT", "isFirstProductEver: ${firstProductManager.isFirstProductEver()}")
            Log.d("SHOOPT_FIRST_PRODUCT", "wasCongratulationShown: ${firstProductManager.wasCongratulationShown()}")
            Log.d("SHOOPT_FIRST_PRODUCT", "shouldShowCongratulation: ${firstProductManager.shouldShowCongratulation()}")

        } catch (e: Exception) {
            Log.e("SHOOPT_FIRST_PRODUCT", "Erreur lors du reset", e)
        }
    }

    /**
     * Affiche le statut actuel du premier produit
     */
    fun logCurrentStatus(context: Context) {
        try {
            val firstProductManager = FirstProductManager.getInstance(context)

            Log.d("SHOOPT_FIRST_PRODUCT", "=== STATUT ACTUEL ===")
            Log.d("SHOOPT_FIRST_PRODUCT", "isFirstProductEver: ${firstProductManager.isFirstProductEver()}")
            Log.d("SHOOPT_FIRST_PRODUCT", "wasCongratulationShown: ${firstProductManager.wasCongratulationShown()}")
            Log.d("SHOOPT_FIRST_PRODUCT", "shouldShowCongratulation: ${firstProductManager.shouldShowCongratulation()}")
            Log.d("SHOOPT_FIRST_PRODUCT", "firstProductTimestamp: ${firstProductManager.getFirstProductTimestamp()}")

        } catch (e: Exception) {
            Log.e("SHOOPT_FIRST_PRODUCT", "Erreur lors de l'affichage du statut", e)
        }
    }

    /**
     * Force l'affichage de la félicitation pour tester
     */
    fun forceShowCongratulation(context: Context) {
        try {
            // Reset d'abord
            resetFirstProductStatus(context)

            Log.d("SHOOPT_FIRST_PRODUCT", "=== AFFICHAGE FORCE DE LA FELICITATION ===")

            // Créer et afficher le dialog directement
            if (context is android.app.Activity) {
                val dialog = com.dedoware.shoopt.ui.dialogs.FirstProductCongratulationDialog(context) {
                    Log.d("SHOOPT_FIRST_PRODUCT", "Dialog de test fermé")
                }
                dialog.show()
            }

        } catch (e: Exception) {
            Log.e("SHOOPT_FIRST_PRODUCT", "Erreur lors de l'affichage forcé", e)
        }
    }
}
