package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.LocalProductRepository
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.ContextualGuideManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UnifiedGuideSystem
import com.dedoware.shoopt.utils.UserPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    private lateinit var updateShoppingListImageButton: ImageButton
    private lateinit var addOrUpdateProductImageButton: ImageButton
    private lateinit var trackShoppingImageButton: ImageButton
    private lateinit var analyseImageButton: ImageButton
    private lateinit var addOrUpdateProductTextView: TextView
    private lateinit var trackShoppingTextView: TextView
    private lateinit var analyseTextView: TextView
    private lateinit var userPreferences: UserPreferences
    private lateinit var guideManager: ContextualGuideManager

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

            // NOUVEAU : Système de guide unifié simplifié
            initializeUnifiedGuideSystem()

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

    /**
     * NOUVEAU : Initialisation du système de guide unifié
     */
    private fun initializeUnifiedGuideSystem() {
        try {
            CrashlyticsManager.log("=== DEBUT initializeUnifiedGuideSystem ===")
            
            // Vérifier si on doit forcer le replay d'un guide
            val forceReplay = intent.getBooleanExtra("force_replay_guide", false)
            val guideType = intent.getStringExtra("guide_type")

            CrashlyticsManager.log("ForceReplay: $forceReplay, GuideType: $guideType")

            if (forceReplay && guideType == "first_product") {
                // Forcer le replay après un délai pour laisser l'interface se charger
                findViewById<View>(android.R.id.content).postDelayed({
                    CrashlyticsManager.log("Lancement du guide forcé")
                    showFirstProductGuide(forceShow = true)
                }, 800)

                // Nettoyer les extras
                intent.removeExtra("force_replay_guide")
                intent.removeExtra("guide_type")

                CrashlyticsManager.log("Replay du guide forcé depuis les paramètres")
                return
            }

            // Vérifier si c'est la première utilisation pour afficher le guide du premier produit
            CrashlyticsManager.log("Vérification pour afficher le guide du premier produit")
            checkAndShowFirstProductGuide()

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur initialisation guide unifié: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Vérifie s'il faut afficher le guide du premier produit
     */
    private fun checkAndShowFirstProductGuide() {
        CrashlyticsManager.log("=== DEBUT checkAndShowFirstProductGuide ===")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CrashlyticsManager.log("Création du productRepository...")
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

                CrashlyticsManager.log("Récupération des produits...")
                val products = productRepository.getAll()
                CrashlyticsManager.log("Nombre de produits trouvés: ${products.size}")

                withContext(Dispatchers.Main) {
                    // Si aucun produit n'existe, afficher le guide après un délai
                    if (products.isEmpty()) {
                        CrashlyticsManager.log("Aucun produit trouvé - programmation affichage du guide dans 1s")
                        findViewById<View>(android.R.id.content).postDelayed({
                            CrashlyticsManager.log("Lancement showFirstProductGuide maintenant")
                            showFirstProductGuide()
                        }, 1000) // Délai pour laisser l'interface se charger
                    } else {
                        CrashlyticsManager.log("Produits existants trouvés - pas d'affichage du guide")
                    }
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur vérification premiers produits: ${e.message}")
                CrashlyticsManager.logException(e)
            }
        }
    }

    /**
     * Affiche le guide pour l'ajout du premier produit
     */
    private fun showFirstProductGuide(forceShow: Boolean = false) {
        try {
            CrashlyticsManager.log("=== DEBUT showFirstProductGuide (forceShow=$forceShow) ===")

            val addProductCard: MaterialCardView = findViewById(R.id.add_product_card)
            val rootView = findViewById<ViewGroup>(android.R.id.content)

            CrashlyticsManager.log("Récupération des vues - addProductCard: $addProductCard, rootView: $rootView")

            val guideSystem = UnifiedGuideSystem.getInstance()
            CrashlyticsManager.log("UnifiedGuideSystem obtenu: $guideSystem")

            val guide = guideSystem.getFirstProductGuide(addProductCard)
            CrashlyticsManager.log("Guide créé: ${guide.id}")

            // Personnaliser le guide avec un callback pour détecter quand l'utilisateur ajoute un produit
            val enhancedGuide = guide.copy(
                onComplete = {
                    CrashlyticsManager.log("Guide premier produit terminé")
                    AnalyticsManager.logSelectContent("tutorial", "guide", "first_product_completed")

                    // Marquer que l'utilisateur a vu le guide
                    userPreferences.setFirstProductGuideShown()
                }
            )

            CrashlyticsManager.log("Appel showGuide...")
            guideSystem.showGuide(
                context = this,
                parentView = rootView,
                guide = enhancedGuide,
                forceShow = forceShow
            )

            CrashlyticsManager.log("Guide premier produit affiché avec succès")
            AnalyticsManager.logSelectContent("tutorial", "guide", "first_product_shown")

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur affichage guide premier produit: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Méthode pour déclencher le guide d'analyse après l'ajout du premier produit
     */
    private fun showAnalysisGuideAfterFirstProduct() {
        try {
            val analyseCard: MaterialCardView = findViewById(R.id.analyse_card)
            val rootView = findViewById<ViewGroup>(android.R.id.content)

            val guideSystem = UnifiedGuideSystem.getInstance()
            val guide = guideSystem.getAnalysisGuide(analyseCard)

            val enhancedGuide = guide.copy(
                onComplete = {
                    CrashlyticsManager.log("Guide analyse terminé")
                    AnalyticsManager.logSelectContent("tutorial", "guide", "analysis_completed")
                }
            )

            // Afficher après un délai pour laisser l'utilisateur revenir à MainActivity
            findViewById<View>(android.R.id.content).postDelayed({
                guideSystem.showGuide(
                    context = this,
                    parentView = rootView,
                    guide = enhancedGuide,
                    forceShow = true
                )
            }, 500)

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur affichage guide analyse: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onResume() {
        super.onResume()

        // Vérifier si l'utilisateur revient après avoir ajouté son premier produit
        checkForFirstProductCompletion()
    }

    /**
     * Vérifie si l'utilisateur vient de terminer l'ajout de son premier produit
     * et affiche les félicitations finales avec le guide vers l'analyse
     */
    private fun checkForFirstProductCompletion() {
        try {
            // Vérifier si l'utilisateur vient d'ajouter son premier produit
            val prefs = getSharedPreferences("unified_guide_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("show_analysis_guide", false)) {
                // Attendre que la vue soit complètement chargée
                findViewById<View>(android.R.id.content).postDelayed({
                    showAnalysisGuideAfterFirstProduct()
                    // Nettoyer le flag
                    prefs.edit().putBoolean("show_analysis_guide", false).apply()
                }, 800)
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la vérification de la complétion du premier produit: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Active le guide contextuel pour les nouveaux utilisateurs
     */
    private fun activateContextualGuideIfNeeded() {
        try {
            if (shouldShowFirstProductGuide()) {
                // Attendre que la vue soit complètement chargée avant d'afficher le guide
                findViewById<View>(android.R.id.content).post {
                    showFirstProductGuide()
                }
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'activation du guide contextuel: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Nettoyer le système de guide unifié
        UnifiedGuideSystem.getInstance().dismissCurrentGuide()
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
                    showAddProductOptions()
                } catch (e: Exception) {
                    // Capture des erreurs
                    CrashlyticsManager.log("Erreur lors de l'affichage des options d'ajout de produit: ${e.message ?: "Message non disponible"}")
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
                        intent.putExtra("from_guide", true) // Indiquer que cela vient du guide
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
                        intent.putExtra("from_guide", true) // Indiquer que cela vient du guide
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

                // Transmettre le flag from_guide si nous sommes dans le contexte du guide
                addProductIntent.putExtra("from_guide", true)

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
                    AnalyticsManager.logCustomEvent("existing_product_loaded", Bundle().apply {
                        putString("source", "barcode_scan")
                        putString("product_found", "true")
                    })
                } else {
                    // Si le produit n'existe pas, simplement passer le code-barres
                    addProductIntent.putExtra("barcode", barcode)

                    // Analytics pour la création d'un nouveau produit
                    AnalyticsManager.logCustomEvent("new_product_created", Bundle().apply {
                        putString("source", "barcode_scan")
                        putString("product_found", "false")
                    })
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

    /**
     * Version simplifiée pour vérifier si on doit montrer le guide
     */
    private fun shouldShowFirstProductGuide(): Boolean {
        return !userPreferences.isFirstProductGuideShown()
    }
}
