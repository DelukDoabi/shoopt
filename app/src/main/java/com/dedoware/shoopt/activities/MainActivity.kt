package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UpdateManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.android.play.core.install.model.InstallStatus
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
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

    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        try {
            if (result.contents == null) {
                Toast.makeText(this@MainActivity, getString(R.string.cancelled), Toast.LENGTH_LONG).show()
            } else {
                try {
                    // Vérifier si le produit existe déjà avant de lancer l'activité
                    checkProductExistenceAndNavigate(result.contents)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du traitement du code-barres: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_processing")
                    CrashlyticsManager.setCustomKey("barcode", result.contents)
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, getString(R.string.barcode_processing_error), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du traitement du résultat du scan: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "barcode_scan_result")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.logException(e)
        }
    }

    private fun checkProductExistenceAndNavigate(barcode: String) {
        // Utilisation de coroutines pour vérifier l'existence du produit en arrière-plan
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
                    AnalyticsManager.logCustomEvent("new_product_scan", params)
                }
                
                startActivity(addProductIntent)
                
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la vérification du produit existant: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "product_existence_check")
                CrashlyticsManager.logException(e)
                
                // En cas d'erreur, essayer de poursuivre avec juste le code-barres
                val addProductIntent = Intent(this@MainActivity, AddProductActivity::class.java)
                addProductIntent.putExtra("barcode", barcode)
                startActivity(addProductIntent)
            }
        }
    }

    private fun showAddProductOptions() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.choose_option))
            .setItems(
                arrayOf(
                    getString(R.string.scan_barcode),
                    getString(R.string.add_product_manually)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        // Option: Scan barcode or add manually
                        try {
                            AnalyticsManager.logUserAction("scan_barcode", "product")

                            // Configuration pour utiliser HorizontalCaptureActivity
                            val options = ScanOptions()
                            options.setPrompt(getString(R.string.scan_barcode_prompt))
                            options.setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                            options.setBeepEnabled(true)
                            options.setBarcodeImageEnabled(true)

                            // Configuration spécifique pour le mode horizontal
                            options.setCameraId(0) // Utiliser la caméra arrière
                            options.setOrientationLocked(true) // Verrouiller l'orientation
                            options.setCaptureActivity(HorizontalCaptureActivity::class.java) // Utiliser notre activité personnalisée pour la capture horizontale
                            options.setTimeout(10000) // Timeout après 10 secondes

                            barcodeLauncher.launch(options)
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors du lancement du scanner de code-barres: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.logException(e)
                            Toast.makeText(this, getString(R.string.scanner_launch_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        // Option: Add product manually
                        try {
                            AnalyticsManager.logUserAction("add_manually", "product")
                            startActivity(Intent(this, AddProductActivity::class.java))
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors du lancement de AddProductActivity: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.logException(e)
                            Toast.makeText(this, getString(R.string.product_screen_open_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UpdateManager.UPDATE_REQUEST_CODE) {
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
        }
    }
}
