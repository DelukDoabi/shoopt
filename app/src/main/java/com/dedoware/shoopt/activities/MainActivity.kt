package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.LocalProductRepository
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UpdateManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// Imports pour le système de spotlight
import com.dedoware.shoopt.models.SpotlightShape
import com.dedoware.shoopt.models.SpotlightItem
import com.dedoware.shoopt.utils.SpotlightManager
import com.dedoware.shoopt.utils.OnboardingManager


class MainActivity : AppCompatActivity() {
    private lateinit var updateShoppingListImageButton: ImageButton
    private lateinit var addOrUpdateProductImageButton: ImageButton
    private lateinit var trackShoppingImageButton: ImageButton
    private lateinit var analyseImageButton: ImageButton
    private lateinit var addOrUpdateProductTextView: TextView
    private lateinit var trackShoppingTextView: TextView
    private lateinit var analyseTextView: TextView
    private lateinit var userPreferences: UserPreferences

    private val useFirebase = false // Définition cohérente avec les autres activités

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Initialiser et appliquer les préférences utilisateur
            userPreferences = UserPreferences(this)
            userPreferences.applyTheme()

            setContentView(R.layout.activity_main)

            setMainVariables()

            // Enregistrement de l'écran principal dans Analytics
            try {
                AnalyticsManager.logScreenView("Main", "MainActivity")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
            }

            // Check if the user is signed in
            try {
                val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser
                currentUser?.let {
                    val username = it.displayName
                    if (username != null) {
                        val toast = Toast.makeText(this, getString(R.string.welcome_user, username), Toast.LENGTH_LONG)
                        toast.show()
                    }
                }
            } catch (e: Exception) {
                // Capture des erreurs liées à l'accès aux informations utilisateur Firebase
                CrashlyticsManager.log("Erreur lors de l'accès aux informations utilisateur: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "firebase_user_access")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            // Configuration des boutons MaterialButton au lieu de ImageButton pour settings et logout
            val logoutButton: MaterialButton = findViewById(R.id.logout_button)
            logoutButton.setOnClickListener {
                displayLogoutConfirmation()
            }

            val settingsButton: MaterialButton = findViewById(R.id.settings_button)
            settingsButton.setOnClickListener {
                try {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } catch (e: Exception) {
                    // Capture des erreurs liées au lancement de l'activité Settings
                    CrashlyticsManager.log("Erreur lors du lancement de SettingsActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "launch_activity")
                    CrashlyticsManager.setCustomKey("target_activity", "SettingsActivity")
                    CrashlyticsManager.logException(e)

                    // Afficher un message à l'utilisateur
                    Toast.makeText(this, getString(R.string.settings_open_error), Toast.LENGTH_SHORT).show()
                }
            }

            // Configuration des cartes pour une meilleure expérience utilisateur
            setupFeatureCards()

            // Vérifier si l'onboarding introduction doit être démarré
            if (!UserPreferences.isOnboardingCompleted(this)) {
                OnboardingManager.checkAndStartOnboarding(this)
                return // L'activité sera fermée par OnboardingManager
            }

            // Si l'introduction est terminée, configurer les spotlights
            setupSpotlightTour()

            // Vérification des mises à jour disponibles
            try {
                val rootView = findViewById<View>(android.R.id.content)
                UpdateManager.checkForUpdate(this, rootView)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la vérification des mises à jour: ${e.message ?: "Message non disponible"}")
            }

        } catch (e: Exception) {
            // Capture des erreurs générales dans onCreate
            CrashlyticsManager.log("Erreur générale dans MainActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "main_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            // Afficher un message à l'utilisateur
            Toast.makeText(this, getString(R.string.app_loading_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: mettre à jour l'intent de l'activité

        // Vérifier si on doit forcer un refresh des spotlights
        val forceRefresh = intent?.getBooleanExtra("force_spotlight_refresh", false) ?: false
        if (forceRefresh) {
            CrashlyticsManager.log("MainActivity onNewIntent: Force spotlight refresh requested")

            // Vérifier si l'onboarding est complété et forcer les spotlights
            if (UserPreferences.isOnboardingCompleted(this)) {
                // Délai court pour laisser l'interface se stabiliser
                window.decorView.postDelayed({
                    setupSpotlightTour()
                }, 500)
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
            CrashlyticsManager.log("Erreur lors de la vérification des mises à jour en attente: "+
                "${e.message ?: "Message non disponible"}")
        }

        // Vérifier si des spotlights doivent être affichés (utile après replay onboarding)
        try {
            // Si l'onboarding est complété mais les spotlights n'ont pas encore été vus
            if (UserPreferences.isOnboardingCompleted(this) &&
                UserPreferences.shouldShowSpotlight(this, "MainActivity")) {

                CrashlyticsManager.log("MainActivity onResume: Triggering spotlights")

                // Délai court pour laisser l'interface se stabiliser
                window.decorView.postDelayed({
                    setupSpotlightTour()
                }, 500)
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la vérification des spotlights en onResume: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Nettoyer les ressources de mise à jour
        UpdateManager.onDestroy()
    }

    private fun setMainVariables() {
        try {
            // Initialisation des boutons et textes
            updateShoppingListImageButton = findViewById(R.id.shopping_list_IB)
            addOrUpdateProductImageButton = findViewById(R.id.add_or_update_product_IB)
            trackShoppingImageButton = findViewById(R.id.track_shopping_IB)
            analyseImageButton = findViewById(R.id.analyse_IB)

            addOrUpdateProductTextView = findViewById(R.id.save_product_TV)
            trackShoppingTextView = findViewById(R.id.track_shopping_TV)
            analyseTextView = findViewById(R.id.analyse_TV)
        } catch (e: Exception) {
            // Capture des erreurs lors de l'initialisation des variables
            CrashlyticsManager.log("Erreur lors de l'initialisation des variables: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "set_main_variables")
            CrashlyticsManager.logException(e)
        }
    }

    // Configuration des cartes de fonctionnalités avec animations de feedback tactile
    private fun setupFeatureCards() {
        try {
            // Configuration de la carte liste d'achats
            val shoppingListCard: MaterialCardView = findViewById(R.id.shopping_list_card)
            shoppingListCard.setOnClickListener {
                try {
                    // Animation de feedback tactile
                    it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()

                    // Analytics pour le passage à l'écran de mise à jour de la liste
                    AnalyticsManager.logSelectContent("navigation", "card", "update_shopping_list")
                    startActivity(Intent(this, UpdateShoppingListActivity::class.java))
                } catch (e: Exception) {
                    // Capture des erreurs
                    CrashlyticsManager.log("Erreur lors du lancement de UpdateShoppingListActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.logException(e)
                    Toast.makeText(this, getString(R.string.shopping_list_open_error), Toast.LENGTH_SHORT).show()
                }
            }

            // Configuration de la carte ajouter/mettre à jour un produit
            val addProductCard: MaterialCardView = findViewById(R.id.add_product_card)
            addProductCard.setOnClickListener {
                try {
                    // Animation de feedback tactile
                    it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()

                    // Analytics pour le passage à l'écran d'ajout de produit
                    AnalyticsManager.logSelectContent("navigation", "card", "add_update_product")

                    // Lancer la nouvelle activité de choix de produit avec animation
                    val intent = Intent(this, ProductChoiceActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                } catch (e: Exception) {
                    // Capture des erreurs
                    CrashlyticsManager.log("Erreur lors du lancement de ProductChoiceActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.logException(e)
                    Toast.makeText(this, getString(R.string.add_product_options_error), Toast.LENGTH_SHORT).show()
                }
            }

            // Configuration de la carte suivi des achats
            val trackShoppingCard: MaterialCardView = findViewById(R.id.track_shopping_card)
            trackShoppingCard.setOnClickListener {
                try {
                    // Animation de feedback tactile
                    it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()

                    // Analytics pour le passage à l'écran de suivi des achats
                    AnalyticsManager.logSelectContent("navigation", "card", "track_shopping")
                    startActivity(Intent(this, TrackShoppingActivity::class.java))
                } catch (e: Exception) {
                    // Capture des erreurs
                    CrashlyticsManager.log("Erreur lors du lancement de TrackShoppingActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.logException(e)
                    Toast.makeText(this, getString(R.string.track_shopping_open_error), Toast.LENGTH_SHORT).show()
                }
            }

            // Configuration de la carte d'analyse
            val analyseCard: MaterialCardView = findViewById(R.id.analyse_card)
            analyseCard.setOnClickListener {
                try {
                    // Animation de feedback tactile
                    it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()

                    // Analytics pour le passage à l'écran d'analyse
                    AnalyticsManager.logSelectContent("navigation", "card", "analyse")
                    startActivity(Intent(this, AnalyseActivity::class.java))
                } catch (e: Exception) {
                    // Capture des erreurs
                    CrashlyticsManager.log("Erreur lors du lancement de AnalyseActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.logException(e)
                    Toast.makeText(this, getString(R.string.analyse_open_error), Toast.LENGTH_SHORT).show()
                }
            }

            // Configuration des ImageButtons dans les cartes pour une redondance d'interaction
            updateShoppingListImageButton.setOnClickListener { shoppingListCard.performClick() }
            addOrUpdateProductImageButton.setOnClickListener { addProductCard.performClick() }
            trackShoppingImageButton.setOnClickListener { trackShoppingCard.performClick() }
            analyseImageButton.setOnClickListener { analyseCard.performClick() }

        } catch (e: Exception) {
            // Capture des erreurs lors de la configuration des cartes
            CrashlyticsManager.log("Erreur lors de la configuration des cartes: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "setup_feature_cards")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Configure et démarre le tour de spotlight pour guider l'utilisateur
     * sur les fonctionnalités principales de l'écran d'accueil
     */
    private fun setupSpotlightTour() {
        try {
            val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            val isForced = prefs.getBoolean("force_spotlights_on_next_resume", false)

            // Vérifier si le spotlight doit être affiché (sauf si forcé)
            if (!isForced && !UserPreferences.shouldShowSpotlight(this, "MainActivity")) {
                CrashlyticsManager.log("MainActivity setupSpotlightTour: Not showing - shouldShow=false, isForced=$isForced")
                return
            }

            CrashlyticsManager.log("MainActivity setupSpotlightTour: Starting spotlight tour (isForced=$isForced)")

            // Créer la liste des éléments à mettre en surbrillance
            val spotlightItems = mutableListOf<SpotlightItem>()

            // Ajouter les cartes principales au spotlight
            val shoppingListCard: MaterialCardView = findViewById(R.id.shopping_list_card)
            val addProductCard: MaterialCardView = findViewById(R.id.add_product_card)
            val trackShoppingCard: MaterialCardView = findViewById(R.id.track_shopping_card)
            val analyseCard: MaterialCardView = findViewById(R.id.analyse_card)
            val settingsButton: MaterialButton = findViewById(R.id.settings_button)
            val logoutButton: MaterialButton = findViewById(R.id.logout_button)

            // Spotlight pour l'ajout de produit
            spotlightItems.add(
                SpotlightItem(
                    targetView = addProductCard,
                    titleRes = R.string.spotlight_main_add_title,
                    descriptionRes = R.string.spotlight_main_add_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour la liste de courses
            spotlightItems.add(
                SpotlightItem(
                    targetView = shoppingListCard,
                    titleRes = R.string.spotlight_main_list_title,
                    descriptionRes = R.string.spotlight_main_list_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le suivi des achats
            spotlightItems.add(
                SpotlightItem(
                    targetView = trackShoppingCard,
                    titleRes = R.string.spotlight_main_scan_title,
                    descriptionRes = R.string.spotlight_main_scan_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour l'analyse
            spotlightItems.add(
                SpotlightItem(
                    targetView = analyseCard,
                    titleRes = R.string.spotlight_analyse_chart_title,
                    descriptionRes = R.string.spotlight_analyse_chart_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour les paramètres
            spotlightItems.add(
                SpotlightItem(
                    targetView = settingsButton,
                    titleRes = R.string.spotlight_main_settings_title,
                    descriptionRes = R.string.spotlight_main_settings_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Spotlight pour la déconnexion
            spotlightItems.add(
                SpotlightItem(
                    targetView = logoutButton,
                    titleRes = R.string.spotlight_main_logout_title,
                    descriptionRes = R.string.spotlight_main_logout_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Supprimer le flag de forçage maintenant qu'on va démarrer
            if (isForced) {
                prefs.edit().remove("force_spotlights_on_next_resume").apply()
                CrashlyticsManager.log("MainActivity setupSpotlightTour: Removed force flag")
            }

            // Démarrer le tour de spotlight avec un léger délai pour que l'interface soit prête
            window.decorView.post {
                CrashlyticsManager.log("MainActivity setupSpotlightTour: Actually starting SpotlightManager")
                SpotlightManager.getInstance(this)
                    .setSpotlightItems(spotlightItems)
                    .setOnCompleteListener {
                        // Callback appelé à la fin du tour
                        CrashlyticsManager.log("MainActivity setupSpotlightTour: Spotlight tour completed")
                        AnalyticsManager.logUserAction(
                            "spotlight_tour_completed",
                            "onboarding",
                            mapOf("screen" to "MainActivity")
                        )
                    }
                    .start("MainActivity")
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du spotlight: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "setup_spotlight_tour")
            CrashlyticsManager.logException(e)
        }
    }

    private fun displayLogoutConfirmation() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.logout_confirmation_title))
            .setMessage(getString(R.string.logout_confirmation_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                try {
                    // Analytics pour déconnexion
                    AnalyticsManager.logUserAction(
                        action = "logout",
                        category = "authentication"
                    )

                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    // Capture des erreurs lors de la déconnexion
                    CrashlyticsManager.log("Erreur lors de la déconnexion: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "user_logout")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, getString(R.string.logout_error), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .create()
            .show()
    }

    // Register the launcher for our new ML Kit-based scanner
    private val barcodeScannerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val barcodeValue = result.data?.getStringExtra(com.dedoware.shoopt.scanner.BarcodeScannerActivity.BARCODE_RESULT)
            if (barcodeValue != null) {
                try {
                    // Vérifier si le produit existe déjà avant de lancer l'activité
                    checkProductExistenceAndNavigate(barcodeValue)

                    // Analytics pour le scan de code-barres
                    val params = Bundle().apply {
                        putString("barcode_length", barcodeValue.length.toString())
                    }
                    AnalyticsManager.logCustomEvent("barcode_scanned", params)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du traitement du code-barres: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_processing")
                    CrashlyticsManager.setCustomKey("barcode", barcodeValue)
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, getString(R.string.barcode_processing_error), Toast.LENGTH_SHORT).show()
                }
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddProductOptions() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.add_product_options_title))
                .setMessage(getString(R.string.add_product_options_message))
                .setPositiveButton(getString(R.string.scan_barcode)) { _, _ ->
                    try {
                        // Analytics pour l'utilisation du scanner de code-barres
                        AnalyticsManager.logUserAction(
                            action = "open_barcode_scanner",
                            category = "product_management"
                        )

                        // Utilisation de notre nouvelle implémentation ML Kit au lieu de ZXing
                        val intent = Intent(this, com.dedoware.shoopt.scanner.BarcodeScannerActivity::class.java)
                        barcodeScannerLauncher.launch(intent)
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors du lancement du scanner: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "launch_barcode_scanner")
                        CrashlyticsManager.logException(e)

                        Toast.makeText(this, getString(R.string.scanner_launch_error), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.manual_entry)) { _, _ ->
                    try {
                        // Analytics pour l'ajout manuel de produit
                        AnalyticsManager.logUserAction(
                            action = "manual_product_entry",
                            category = "product_management"
                        )

                        val intent = Intent(this, AddProductActivity::class.java)
                        startActivity(intent)
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors du lancement de l'activité d'ajout manuel: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.logException(e)

                        Toast.makeText(this, getString(R.string.manual_entry_error), Toast.LENGTH_SHORT).show()
                    }
                }
                .create()
                .show()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage des options d'ajout: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "show_add_product_options")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, getString(R.string.dialog_display_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkProductExistenceAndNavigate(barcode: String) {
        val productRepository: IProductRepository = if (useFirebase) {
            FirebaseProductRepository()
        } else {
            val database = (application as ShooptApplication).database
            LocalProductRepository(
                database.productDao(),
                database.shopDao(),
                database.shoppingCartDao(),
                database.cartItemDao()
            )
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val barcodeAsLong = barcode.toLongOrNull() ?: 0L

                // Vérifier si le produit existe déjà dans la base de données
                val existingProduct = withContext(Dispatchers.IO) {
                    productRepository.getProductByBarcode(barcodeAsLong)
                }

                // Préparer l'intent avec les données appropriées
                val addProductIntent = Intent(this@MainActivity, AddProductActivity::class.java)

                if (existingProduct != null) {
                    // Si le produit existe, ajouter toutes ses informations à l'intent
                    addProductIntent.apply {
                        putExtra("productId", existingProduct.id)
                        putExtra("barcode", existingProduct.barcode)
                        putExtra("name", existingProduct.name)
                        putExtra("shop", existingProduct.shop)
                        putExtra("price", existingProduct.price)
                        putExtra("unitPrice", existingProduct.unitPrice)
                        putExtra("pictureUrl", existingProduct.pictureUrl)
                    }

                    // Analytics pour le chargement d'un produit existant
                    val params = Bundle().apply {
                        putString("source", "barcode_scan")
                        putString("product_found", "true")
                    }
                    AnalyticsManager.logCustomEvent("existing_product_loaded", params)
                } else {
                    // Si le produit n'existe pas, simplement passer le code-barres
                    addProductIntent.putExtra("barcode", barcode)

                    // Analytics pour la création d'un nouveau produit
                    val params = Bundle().apply {
                        putString("source", "barcode_scan")
                        putString("product_found", "false")
                    }
                    AnalyticsManager.logCustomEvent("new_product_created", params)
                }

                startActivity(addProductIntent)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la vérification du produit: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "product_existence_check")
                CrashlyticsManager.setCustomKey("barcode", barcode)
                CrashlyticsManager.logException(e)

                Toast.makeText(this@MainActivity, getString(R.string.product_check_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
