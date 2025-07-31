package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.scanner.BarcodeScannerActivity
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * ProductChoiceActivity - Un écran moderne qui permet à l'utilisateur de choisir
 * entre scanner un code-barres ou saisir manuellement les informations d'un produit.
 */
class ProductChoiceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_choice)

        // Handle back button with the OnBackPressedDispatcher
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

        setupUI()

        // --- Ajout du déclenchement automatique du guide si besoin ---
        val guide = com.dedoware.shoopt.utils.AddFirstProductGuide(this)
        val scanBarcodeCard: android.view.View = findViewById(R.id.scan_barcode_card)
        val manualEntryCard: android.view.View = findViewById(R.id.manual_entry_card)
        if (guide.getCurrentGuideState() == com.dedoware.shoopt.utils.AddFirstProductGuide.GuideState.PRODUCT_CHOICE_SCREEN) {
            guide.showProductChoiceGuide(scanBarcodeCard, manualEntryCard) {
                // Enchaînement automatique : lancer le scanner après la fin du guide
                scanBarcodeCard.performClick()
            }
        }
    }

    private fun setupUI() {
        // Configuration du bouton de retour
        val backButton: MaterialButton = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Configuration de la carte pour scanner un code-barres
        val scanBarcodeCard: MaterialCardView = findViewById(R.id.scan_barcode_card)
        scanBarcodeCard.setOnClickListener {
            try {
                // Animation de feedback tactile
                val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.card_scale)
                it.startAnimation(scaleAnimation)

                // Analytics pour l'utilisation du scanner de code-barres
                AnalyticsManager.logUserAction(
                    action = "open_barcode_scanner",
                    category = "product_management"
                )

                // Lancer l'activité de scan de code-barres
                val intent = Intent(this, BarcodeScannerActivity::class.java)
                barcodeScannerLauncher.launch(intent)

            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du lancement du scanner: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "launch_barcode_scanner_from_choice_activity")
                CrashlyticsManager.logException(e)

                showErrorToast(R.string.scanner_launch_error)
            }
        }

        // Configuration de la carte pour saisie manuelle
        val manualEntryCard: MaterialCardView = findViewById(R.id.manual_entry_card)
        manualEntryCard.setOnClickListener {
            try {
                // Animation de feedback tactile
                val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.card_scale)
                it.startAnimation(scaleAnimation)

                // Analytics pour l'ajout manuel de produit
                AnalyticsManager.logUserAction(
                    action = "manual_product_entry",
                    category = "product_management"
                )

                // Lancer l'activité d'ajout manuel de produit
                val intent = Intent(this, AddProductActivity::class.java).apply {
                    // Ajouter des flags pour revenir à MainActivity après la sauvegarde
                    addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                // Terminer cette activité pour qu'elle ne reste pas dans la pile
                finish()
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du lancement de l'activité d'ajout manuel: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.logException(e)

                showErrorToast(R.string.manual_entry_error)
            }
        }
    }

    // Réutilisation du même ActivityResultLauncher que dans MainActivity
    private val barcodeScannerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Utilisation de la constante BARCODE_RESULT définie dans BarcodeScannerActivity
            val barcode = result.data?.getStringExtra(com.dedoware.shoopt.scanner.BarcodeScannerActivity.BARCODE_RESULT)
            if (barcode != null) {
                try {
                    // Analytics pour le scan réussi
                    AnalyticsManager.logUserAction(
                        action = "barcode_scan_success",
                        category = "product_management"
                    )

                    // Vérifier le produit dans la base de données
                    val intent = Intent(this, AddProductActivity::class.java).apply {
                        putExtra("barcode", barcode)
                        // Ajouter des flags pour revenir à MainActivity après la sauvegarde
                        addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    // Terminer cette activité pour qu'elle ne reste pas dans la pile
                    finish()
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de la vérification du produit: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.logException(e)

                    showErrorToast(R.string.product_check_error)
                }
            }
        }
    }

    private fun showErrorToast(messageResId: Int) {
        android.widget.Toast.makeText(this, getString(messageResId), android.widget.Toast.LENGTH_SHORT).show()
    }
}
