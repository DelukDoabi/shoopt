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
// Imports pour le système de spotlight
import com.dedoware.shoopt.extensions.startSpotlightTour
import com.dedoware.shoopt.extensions.createSpotlightItem
import com.dedoware.shoopt.extensions.isSpotlightAvailable
import com.dedoware.shoopt.models.SpotlightShape
import com.dedoware.shoopt.utils.UserPreferences

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

        // Démarrer le système de spotlight si nécessaire
        setupSpotlightTour()
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

    /**
     * Configure et démarre le tour de spotlight pour guider l'utilisateur
     * sur les options d'ajout de produit disponibles
     */
    private fun setupSpotlightTour() {
        try {
            // Vérifier si le spotlight doit être affiché
            if (!isSpotlightAvailable()) {
                return
            }

            // Créer la liste des éléments à mettre en surbrillance
            val spotlightItems = mutableListOf<com.dedoware.shoopt.models.SpotlightItem>()

            // Récupérer les éléments UI
            val scanBarcodeCard: MaterialCardView = findViewById(R.id.scan_barcode_card)
            val manualEntryCard: MaterialCardView = findViewById(R.id.manual_entry_card)

            // Spotlight pour le scan de code-barres
            spotlightItems.add(
                createSpotlightItem(
                    targetView = scanBarcodeCard,
                    titleRes = R.string.spotlight_choice_scan_title,
                    descriptionRes = R.string.spotlight_choice_scan_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour la saisie manuelle
            spotlightItems.add(
                createSpotlightItem(
                    targetView = manualEntryCard,
                    titleRes = R.string.spotlight_choice_manual_title,
                    descriptionRes = R.string.spotlight_choice_manual_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Démarrer le tour de spotlight avec un léger délai pour que l'interface soit prête
            window.decorView.post {
                startSpotlightTour(spotlightItems) {
                    // Callback appelé à la fin du tour
                    AnalyticsManager.logUserAction(
                        "spotlight_tour_completed",
                        "onboarding",
                        mapOf("screen" to "ProductChoiceActivity")
                    )
                }
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du spotlight: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "setup_spotlight_tour_product_choice")
            CrashlyticsManager.logException(e)
        }
    }

    private fun showErrorToast(messageResId: Int) {
        android.widget.Toast.makeText(this, getString(messageResId), android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: mettre à jour l'intent de l'activité

        // Vérifier si on doit forcer un refresh des spotlights
        val forceRefresh = intent?.getBooleanExtra("force_spotlight_refresh", false) ?: false
        if (forceRefresh) {
            CrashlyticsManager.log("ProductChoiceActivity onNewIntent: Force spotlight refresh requested")

            // Vérifier si l'onboarding est complété et forcer les spotlights
            if (UserPreferences.isOnboardingCompleted(this)) {
                // Délai court pour laisser l'interface se stabiliser
                window.decorView.postDelayed({
                    setupSpotlightTour()
                }, 500)
            }
        }
    }
}
