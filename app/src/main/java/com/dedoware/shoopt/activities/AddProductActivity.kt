package com.dedoware.shoopt.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Matrix
import android.graphics.Rect
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.NestedScrollView
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop
import com.dedoware.shoopt.persistence.FirebaseImageStorage
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import com.dedoware.shoopt.persistence.IImageStorage
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.LocalImageStorage
import com.dedoware.shoopt.persistence.LocalProductRepository
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.scanner.BarcodeScannerActivity
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.ShooptUtils
import com.dedoware.shoopt.utils.UserPreferences
import com.dedoware.shoopt.utils.FirstProductManager
import com.dedoware.shoopt.ui.dialogs.FirstProductCongratulationDialog
import com.dedoware.shoopt.gamification.manager.SimplifiedGamificationManager
import com.dedoware.shoopt.gamification.ui.GamificationCelebrationView
import com.dedoware.shoopt.gamification.data.DefaultAchievements
import com.dedoware.shoopt.gamification.models.UserProfile
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
// Imports pour le système de spotlight
import com.dedoware.shoopt.extensions.startSpotlightTour
import com.dedoware.shoopt.extensions.createSpotlightItem
import com.dedoware.shoopt.extensions.isSpotlightAvailable
import com.dedoware.shoopt.models.SpotlightShape


class AddProductActivity : AppCompatActivity() {
    private lateinit var retrievedProductId: String
    private lateinit var backImageButton: MaterialButton
    private lateinit var productPictureImageButton: ImageButton
    private lateinit var saveProductImageButton: MaterialButton
    private lateinit var productBarcodeEditText: EditText
    private lateinit var productNameEditText: EditText
    private lateinit var productPriceEditText: EditText
    private lateinit var productUnitPriceEditText: EditText
    private lateinit var productShopAutoCompleteTextView: AutoCompleteTextView
    private lateinit var productPictureFile: File
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var autocompletionSwitch: SwitchMaterial
    val shopList = mutableListOf<String>()
    private lateinit var productRepository: IProductRepository
    private lateinit var imageStorage: IImageStorage
    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }
    private val useFirebase = false // This could be a config or user preference

    // Variables pour le système de gamification - version simplifiée
    private lateinit var gamificationManager: SimplifiedGamificationManager
    private var gamificationCelebrationView: GamificationCelebrationView? = null

    // Flag pour savoir si le code-barres a été scanné dans cette session
    private var wasScannedBarcode: Boolean = false

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == Activity.RESULT_OK) {
                    displayProductPictureOnImageButton()

                    // Verifier si l'autocompletion intelligente est activee
                    if (autocompletionSwitch.isChecked) {
                        val pictureUrl = intent.getStringExtra("pictureUrl") ?: ""

                        // Trigger image analysis and shop name auto-completion in parallel
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                showLoadingOverlay()

                                val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

                                // Lancer l'analyse AI seulement si activee
                                if (UserPreferences.isAiAutocompletionEnabled(this@AddProductActivity)) {
                                    jobs.add(async {
                                        analyzeProductImageWithMessage(pictureUrl)
                                    })
                                }

                                // Lancer la localisation Maps seulement si activee
                                if (UserPreferences.isMapsAutocompletionEnabled(this@AddProductActivity)) {
                                    jobs.add(async {
                                        fetchCurrentLocationAndFillShopNameWithMessage()
                                    })
                                }

                                // Attendre que toutes les tâches activees se terminent
                                try {
                                    jobs.forEach { it.await() }
                                } catch (e: Exception) {
                                    CrashlyticsManager.log("Erreur lors de l'attente des tâches asynchrones: ${e.message ?: "Message non disponible"}")
                                    CrashlyticsManager.setCustomKey("error_location", "async_tasks")
                                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                                    CrashlyticsManager.logException(e)
                                } finally {
                                    hideLoadingOverlay()
                                }
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors de l'analyse de l'image ou de la recuperation de l'emplacement: ${e.message ?: "Message non disponible"}")
                                CrashlyticsManager.setCustomKey("error_location", "product_analysis")
                                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                                CrashlyticsManager.logException(e)

                                hideLoadingOverlay()
                            }
                        }
                    } else {
                        // Autocompletion desactivee - afficher un message informatif discret
                        Toast.makeText(this, getString(R.string.autocompletion_disabled_message), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur dans resultLauncher: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "result_launcher")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Log.e("AddProductActivity", "Error in resultLauncher", e)

                Toast.makeText(this, "Une erreur est survenue lors du traitement de l'image", Toast.LENGTH_SHORT).show()
            }
        }

    private var analyzeImageJob: Job? = null

    private lateinit var loadingOverlay: View

    private lateinit var scanBarcodeButton: MaterialButton

    private val loadingMessages = mutableListOf<String>()
    private val loadingMessagesMutex = Mutex()

    private var dotCount = 0
    private var isAnimatingDots = false
    private val dotHandler = Handler(Looper.getMainLooper())

    private suspend fun updateLoadingMessage(message: String, add: Boolean) {
        loadingMessagesMutex.withLock {
            val msg = when (message) {
                "Analyzing image" -> getString(R.string.analyzing_image)
                "Fetching location and shop name" -> getString(R.string.fetching_location_and_shop)
                else -> message
            }
            if (add) {
                loadingMessages.add(msg)
            } else {
                loadingMessages.remove(msg)
            }
            val combinedMessage = loadingMessages.joinToString("\n")
            withContext(Dispatchers.Main) {
                val loadingMessageTextView = findViewById<TextView>(R.id.loading_overlay_message)
                loadingMessageTextView.text = combinedMessage
                loadingMessageTextView.gravity = Gravity.CENTER // Ensure text is centered
            }
        }
    }

    private suspend fun analyzeProductImageWithMessage(pictureUrl: String) {
        updateLoadingMessage("Analyzing image", true)
        try {
            analyzeProductImage(pictureUrl)
        } finally {
            updateLoadingMessage("Analyzing image", false)
        }
    }

    private suspend fun fetchCurrentLocationAndFillShopNameWithMessage() {
        updateLoadingMessage("Fetching location and shop name", true)
        try {
            fetchCurrentLocationAndFillShopName()
        } finally {
            updateLoadingMessage("Fetching location and shop name", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_add_product)

            // Enregistrer l'evenement d'ouverture de l'ecran AddProduct
            AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("AddProduct", "AddProductActivity")

            try {
                productRepository = if (useFirebase) {
                    FirebaseProductRepository()
                } else {
                    LocalProductRepository(
                        database.productDao(),
                        database.shopDao(),
                        database.shoppingCartDao(),
                        database.cartItemDao()
                    )
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation du repository: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "repository_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'application", Toast.LENGTH_SHORT).show()
            }

            try {
                imageStorage = if (useFirebase) {
                    FirebaseImageStorage()
                } else {
                    LocalImageStorage(this)
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation du stockage d'images: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "image_storage_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }


            try {
                setMainVariables()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation des variables: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "main_variables_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            try {
                retrieveProductDataFromIntent()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la recuperation des donnees du produit depuis l'intent: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "intent_data_retrieval")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            try {
                retrieveMainData(savedInstanceState)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la recuperation des donnees sauvegardees: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "saved_instance_data_retrieval")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            if (useFirebase) {
                try {
                    ShooptUtils.doAfterInitFirebase(baseContext) { setShopsData() }
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de l'initialisation Firebase: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "firebase_init")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)
                }
            } else {
                try {
                    setShopsData()
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de l'initialisation des donnees de magasins: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "shops_data_init")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)
                }
            }

            try {
                productPictureFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "temp_shoopt_product_picture.jpg"
                )
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la creation du fichier image temporaire: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "temp_file_creation")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            try {
                getScannedBarcode()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la recuperation du code-barres scanne: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "barcode_retrieval")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            backImageButton.setOnClickListener {
                finish()
            }

            productPictureImageButton.setOnClickListener {
                try {
                    launchCamera()
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement de la camera: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "camera_launch")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible de lancer l'appareil photo", Toast.LENGTH_SHORT).show()
                }
            }

            saveProductImageButton.setOnClickListener {
                try {
                    if (intent.hasExtra("productId")) {
                        val pictureUrl = intent.getStringExtra("pictureUrl")
                        val price = productPriceEditText.text.toString()
                        val unitPrice = productUnitPriceEditText.text.toString()

                        if (!price.isNullOrEmpty() && !unitPrice.isNullOrEmpty()) {
                            pictureUrl?.let { saveProduct(it, price, unitPrice) }
                        } else {
                            // Handle missing price or unit price
                            Toast.makeText(this, getString(R.string.price_unit_required), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        saveAllProductData()
                    }
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de la sauvegarde du produit: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "product_save_button")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Erreur lors de la sauvegarde du produit", Toast.LENGTH_SHORT).show()
                }
            }

            try {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation du client de localisation: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "location_client_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            // Initialize Places API
            try {
                val remoteConfig = Firebase.remoteConfig
                remoteConfig.fetch(0).addOnCompleteListener { fetchTask ->
                    if (fetchTask.isSuccessful) {
                        remoteConfig.activate().addOnCompleteListener { activateTask ->
                            if (activateTask.isSuccessful) {
                                Log.d("DEBUG", "Remote Config fetch and activate successful.")
                                val mapsApiKey = remoteConfig.getString("MAPS_KEY")
                                Log.d("DEBUG", "Fetched MAPS_KEY: $mapsApiKey")

                                if (mapsApiKey.isNotEmpty()) {
                                    try {
                                        Places.initialize(applicationContext, mapsApiKey)
                                        placesClient = Places.createClient(this)
                                    } catch (e: Exception) {
                                        CrashlyticsManager.log("Erreur lors de l'initialisation de Places API: ${e.message ?: "Message non disponible"}")
                                        CrashlyticsManager.setCustomKey("error_location", "places_api_init")
                                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                                        CrashlyticsManager.logException(e)
                                    }
                                } else {
                                    Log.e("DEBUG", "MAPS_KEY is empty. Places API initialization skipped.")
                                    CrashlyticsManager.log("MAPS_KEY vide, initialisation de Places API ignoree")
                                    CrashlyticsManager.setCustomKey("error_location", "maps_key_empty")
                                }
                            } else {
                                Log.e("DEBUG", "Remote Config activation failed.", activateTask.exception)
                                activateTask.exception?.let {
                                    CrashlyticsManager.log("Échec de l'activation de Remote Config: ${it.message ?: "Message non disponible"}")
                                    CrashlyticsManager.setCustomKey("error_location", "remote_config_activate")
                                    CrashlyticsManager.setCustomKey("exception_class", it.javaClass.name)
                                    CrashlyticsManager.setCustomKey("exception_message", it.message ?: "Message non disponible")
                                    CrashlyticsManager.logException(it)
                                }
                            }
                        }
                    } else {
                        Log.e("DEBUG", "Remote Config fetch failed.", fetchTask.exception)
                        fetchTask.exception?.let {
                            CrashlyticsManager.log("Échec de la recuperation de Remote Config: ${it.message ?: "Message non disponible"}")
                            CrashlyticsManager.setCustomKey("error_location", "remote_config_fetch")
                            CrashlyticsManager.setCustomKey("exception_class", it.javaClass.name)
                            CrashlyticsManager.setCustomKey("exception_message", it.message ?: "Message non disponible")
                            CrashlyticsManager.logException(it)
                        }
                    }

                    if (!::placesClient.isInitialized) {
                        Log.e("DEBUG", "placesClient is not initialized. Skipping location fetch.")
                        return@addOnCompleteListener
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            fetchCurrentLocationAndFillShopName()
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors de la recuperation de la localisation: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.setCustomKey("error_location", "location_fetch")
                            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                            CrashlyticsManager.logException(e)
                        }
                    }
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de Remote Config: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "remote_config_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            // Inflate and configure the loading overlay
            try {
                val inflater = LayoutInflater.from(this)
                loadingOverlay = inflater.inflate(R.layout.loading_overlay, null)
                loadingOverlay.visibility = View.GONE
                val rootLayout = findViewById<ViewGroup>(android.R.id.content)
                loadingOverlay = inflater.inflate(R.layout.loading_overlay, rootLayout, false)
                rootLayout.addView(loadingOverlay)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de l'overlay de chargement: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "loading_overlay_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            try {
                scanBarcodeButton = findViewById(R.id.scan_barcode_IB)
                Log.d("DEBUG_SCANNER", "Button found: ${scanBarcodeButton != null}")

                scanBarcodeButton.setOnClickListener {
                    Log.d("DEBUG_SCANNER", "Button clicked, calling launchBarcodeScanner()")
                    try {
                        launchBarcodeScanner()
                    } catch (e: Exception) {
                        Log.e("DEBUG_SCANNER", "Error in launchBarcodeScanner: ${e.message}", e)
                        CrashlyticsManager.log("Erreur lors du lancement du scanner de code-barres: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "barcode_scanner_launch")
                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                        CrashlyticsManager.logException(e)

                        Toast.makeText(this, "Impossible de lancer le scanner", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DEBUG_SCANNER", "Error setting up barcode button: ${e.message}", e)
                CrashlyticsManager.log("Erreur lors de l'initialisation du bouton de scan: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "scan_button_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            // --- Onboarding simplifie - plus de guide complexe ---

            // Demarrer le système de spotlight si necessaire
            setupSpotlightTour()

            // AJOUT TEMPORAIRE POUR TESTER LA FELICITATION
            // À supprimer une fois que ça fonctionne !
            try {
                // Utiliser une approche alternative pour détecter le mode debug
                val isDebugMode = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

                if (isDebugMode) {
                    // Ajouter un bouton de test long-press sur le bouton de sauvegarde
                    saveProductImageButton.setOnLongClickListener {
                        try {
                            Log.d("SHOOPT_FIRST_PRODUCT", "=== TEST FORCE DE LA FELICITATION ===")

                            // Reset et affichage forcé
                            com.dedoware.shoopt.utils.FirstProductTestHelper.resetFirstProductStatus(this)

                            // Afficher immédiatement
                            showFirstProductCongratulation()

                            Toast.makeText(this, "Test félicitation forcé!", Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            Log.e("SHOOPT_FIRST_PRODUCT", "Erreur test félicitation", e)
                            Toast.makeText(this, "Erreur test: ${e.message}", Toast.LENGTH_SHORT).show()
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore si la détection du mode debug échoue
                Log.w("AddProductActivity", "Could not setup debug test features", e)
            }

            // Initialiser le manager de gamification simplifié
            try {
                gamificationManager = SimplifiedGamificationManager.getInstance(this)
                Log.d("GAMIFICATION", "GamificationManager simplifié initialisé avec succès")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de GamificationManager: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "gamification_manager_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans onCreate d'AddProductActivity: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "add_product_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Log.e("AddProductActivity", "Error in resultLauncher", e)

            // Affichage d'un message d'erreur à l'utilisateur
            Toast.makeText(this, "Une erreur est survenue lors du demarrage de l'application. Veuillez reessayer.", Toast.LENGTH_LONG).show()

            // Fermer l'activite en cas d'erreur critique
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)

        outState.putParcelable("productPicture", productPictureImageButton.drawable.toBitmap())
        outState.putString("productBarCode", productBarcodeEditText.text.toString())
        outState.putString("productName", productNameEditText.text.toString())
        outState.putString("productPrice", productPriceEditText.text.toString())
        outState.putString("productUnitPrice", productUnitPriceEditText.text.toString())
        outState.putString("productShop", productShopAutoCompleteTextView.text.toString())
    }

    private fun setShopsData() {
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        productShopAutoCompleteTextView.setAdapter(adapter)

        updateShopList(adapter)

        // Scroll automatique vers le champ shop
        productShopAutoCompleteTextView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToShopField()
            }
        }

        productShopAutoCompleteTextView.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                scrollToShopField()
            }
        })

        productShopAutoCompleteTextView.setOnClickListener {
            productShopAutoCompleteTextView.showDropDown()
            scrollToShopField()
        }
    }

    private fun updateShopList(adapter: ArrayAdapter<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val shops = withContext(Dispatchers.IO) {
                    productRepository.getShops()
                }
                shopList.clear()
                shopList.addAll(shops)
                adapter.clear()
                adapter.addAll(shopList)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.d("SHOOPT_TAG", "Failed to load shops.", e)
                Toast.makeText(
                    this@AddProductActivity,
                    getString(R.string.failed_to_load_shops, e.localizedMessage ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun addNewShop(shopName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    productRepository.addShop(Shop(shopName))
                }
                if (success) {
                    Log.d("SHOOPT_TAG", "New shop added successfully!")
                    Toast.makeText(this@AddProductActivity, getString(R.string.new_shop_added), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AddProductActivity, getString(R.string.failed_to_add_shop), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.d("SHOOPT_TAG", "Failed to add shop.", e)
                Toast.makeText(this@AddProductActivity, "Error adding shop: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Show photo tips dialog only if autocompletion is enabled AND user hasn't disabled the tips
            if (autocompletionSwitch.isChecked && UserPreferences.shouldShowPhotoTips(this)) {
                showPhotoTipsDialog()
            } else {
                launchCameraInternal()
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun showPhotoTipsDialog() {
        try {
            // Create dialog with custom layout
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_photo_tips, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            // Set up dialog window with rounded corners
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_background)

            // Find views in dialog layout
            val continueButton = dialogView.findViewById<MaterialButton>(R.id.continue_button)
            val dontShowAgainCheckbox = dialogView.findViewById<CheckBox>(R.id.dont_show_again_checkbox)

            // Set up button click listener
            continueButton.setOnClickListener {
                // Save user preference if "Don't show again" is checked
                if (dontShowAgainCheckbox.isChecked) {
                    UserPreferences.setPhotoTipsEnabled(this, false)
                }

                // Dismiss dialog and launch camera
                dialog.dismiss()
                launchCameraInternal()
            }

            // Show the dialog
            dialog.show()

            // Optional: Track analytics for dialog shown
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("photo_tips_dialog_shown", null)
        } catch (e: Exception) {
            // Fallback to camera launch if dialog fails
            CrashlyticsManager.log("Error showing photo tips dialog: ${e.message ?: "Unknown error"}")
            CrashlyticsManager.logException(e)
            launchCameraInternal()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            Toast.makeText(this, "Permission camera refusee", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCameraInternal() {
        // Analytique pour le lancement de la camera
        val selectParams1 = android.os.Bundle().apply {
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.CONTENT_TYPE, "product_picture")
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_ID, "camera")
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_NAME, "product_capture")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SELECT_CONTENT, selectParams1)

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val productPictureUri =
            FileProvider.getUriForFile(this, "com.dedoware.shoopt.fileprovider", productPictureFile)

        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, productPictureUri)

        resultLauncher.launch(cameraIntent)
    }

    private fun launchBarcodeScanner() {
        // Analytique pour le scan de code-barres
        val selectParams2 = android.os.Bundle().apply {
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.CONTENT_TYPE, "barcode_scan")
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_ID, "scanner")
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_NAME, "product_barcode")
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SELECT_CONTENT, selectParams2)
        try {
            // Utilisation de notre nouvelle implementation basee sur ML Kit
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("BarcodeScanner", "Erreur lors du lancement du scanner: ${e.message}", e)
            Toast.makeText(this, getString(R.string.scanner_launch_error), Toast.LENGTH_LONG).show()
        }
    }

    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcodeValue = result.data?.getStringExtra(BarcodeScannerActivity.BARCODE_RESULT)
            if (barcodeValue != null) {
                productBarcodeEditText.setText(barcodeValue)

                // Marquer que le code-barres provient du scanner pour tracker correctement l'ajout
                wasScannedBarcode = true

                // Analytics pour le code-barres scanne
                val params = Bundle().apply {
                    putString("barcode_length", barcodeValue.length.toString())
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("barcode_scanned", params)

                // Tracker le succès du scan
                try {
                    AnalyticsService.getInstance(ShooptApplication.instance).trackScanSuccess("barcode")
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du tracking du succès du scan: ${e.message}")
                }
            } else {
                // Pas de valeur retournée -> considérer comme échec
                try {
                    AnalyticsService.getInstance(ShooptApplication.instance).trackScanFailed("barcode", "no_value_returned")
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du tracking de l'échec du scan (pas de valeur): ${e.message}")
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_LONG).show()
            try {
                AnalyticsService.getInstance(ShooptApplication.instance).trackScanFailed("barcode", "user_cancelled")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du tracking de l'annulation du scan: ${e.message}")
            }
        }
    }

    private fun sanitizePriceInput(price: String): String {
        return price.replace(",", ".").replace(Regex("[^0-9.]"), "")
    }

    // Update the saveAllProductData function to sanitize price inputs
    private fun saveAllProductData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val productBarcode = try {
                    productBarcodeEditText.text.toString().toLong()
                } catch (e: NumberFormatException) {
                    CrashlyticsManager.log("Erreur de conversion du code-barres: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_parsing")
                    CrashlyticsManager.setCustomKey("barcode_input", productBarcodeEditText.text.toString())
                    CrashlyticsManager.logException(e)
                    Toast.makeText(this@AddProductActivity, "Le code-barres doit être un nombre valide", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val productName = productNameEditText.text.toString()
                val productShop = productShopAutoCompleteTextView.text.toString()

                // Sanitize price inputs
                val sanitizedPrice = sanitizePriceInput(productPriceEditText.text.toString())
                val sanitizedUnitPrice = sanitizePriceInput(productUnitPriceEditText.text.toString())

                if (sanitizedPrice.isEmpty() || sanitizedUnitPrice.isEmpty()) {
                    Toast.makeText(this@AddProductActivity, getString(R.string.price_unit_required), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    val pictureUrl = saveProductImage(getProductPictureData(), "product-pictures/$productBarcode-$productName-$productShop.jpg")

                    if (pictureUrl != null) {
                        saveShop(productShop)
                        saveProduct(pictureUrl, sanitizedPrice, sanitizedUnitPrice)
                    } else {
                        CrashlyticsManager.log("Échec de l'upload de l'image du produit")
                        CrashlyticsManager.setCustomKey("error_location", "product_image_upload")
                        Toast.makeText(this@AddProductActivity, "Échec de l'upload de l'image du produit", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de la sauvegarde de l'image: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "product_image_save")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this@AddProductActivity, "Erreur lors de la sauvegarde de l'image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur globale lors de la sauvegarde du produit: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "save_product_data")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Log.d("SHOOPT_TAG", "Error saving product.", e)
                Toast.makeText(this@AddProductActivity, "Error saving product: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Update the saveProduct function with simplified gamification integration
    private fun saveProduct(productPictureUrl: String, price: String, unitPrice: String) {
        val barcode = if (productBarcodeEditText.text.toString().isEmpty()) "0".toLong() else productBarcodeEditText.text.toString().toLong()
        val timestamp = System.currentTimeMillis()
        val name = productNameEditText.text.toString()
        val shop = productShopAutoCompleteTextView.text.toString()

        if (name.isNotEmpty() && price.isNotEmpty() && unitPrice.isNotEmpty() && shop.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Vérifier si c'est le premier produit avant la sauvegarde
                    val firstProductManager = FirstProductManager.getInstance(this@AddProductActivity)
                    val isFirstProduct = firstProductManager.shouldShowCongratulation()
                    val isNewProduct = !intent.hasExtra("productId")

                    // LOGS DE DEBUG pour diagnostiquer
                    Log.d("SHOOPT_FIRST_PRODUCT", "=== DEBUT VERIFICATION PREMIER PRODUIT ===")
                    Log.d("SHOOPT_FIRST_PRODUCT", "isFirstProductEver: ${firstProductManager.isFirstProductEver()}")
                    Log.d("SHOOPT_FIRST_PRODUCT", "wasCongratulationShown: ${firstProductManager.wasCongratulationShown()}")
                    Log.d("SHOOPT_FIRST_PRODUCT", "shouldShowCongratulation: $isFirstProduct")
                    Log.d("SHOOPT_FIRST_PRODUCT", "Intent has productId: ${intent.hasExtra("productId")}")

                    val productId = withContext(Dispatchers.IO) {
                        if (intent.hasExtra("productId")) {
                            // C'est une MISE À JOUR, pas un nouveau produit
                            val product = Product(retrievedProductId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl)
                            productRepository.update(product)

                            // Appel de tracking pour modification de produit (anonymisé: nom vide)
                            try {
                                AnalyticsService.getInstance(ShooptApplication.instance).trackProductModify(product.id, product.name)
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors du tracking de la modification de produit: ${e.message}")
                            }

                            // Analytique anonymisée pour la mise à jour de produit (ancien log conservé)
                            val params = Bundle().apply {
                                putString("has_barcode", (barcode != 0L).toString())
                            }
                            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("product_updated", params)

                            product.id
                        } else {
                            // C'est un NOUVEAU produit
                            var repositoryProductId = productRepository.getUniqueId()

                            if (repositoryProductId == null) {
                                repositoryProductId = UUID.randomUUID().toString()
                            }

                            val product = Product(repositoryProductId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl)
                            val productIdInserted = productRepository.insert(product)

                            // IMPORTANT: Marquer le premier produit comme ajouté SEULEMENT pour les nouveaux produits
                            if (isFirstProduct) {
                                Log.d("SHOOPT_FIRST_PRODUCT", "Marquage du premier produit comme ajouté")
                                firstProductManager.markFirstProductAdded()
                            }

                            // Tracking : différencier ajout manuel vs ajout via scan
                            try {
                                if (wasScannedBarcode) {
                                    AnalyticsService.getInstance(ShooptApplication.instance).trackProductAddScan(productIdInserted, product.name)
                                } else {
                                    AnalyticsService.getInstance(ShooptApplication.instance).trackProductAddManual(productIdInserted, product.name)
                                }
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors du tracking de l'ajout de produit: ${e.message}")
                            }

                            // Analytique anonymisée pour l'ajout de produit (ancien log conservé)
                            val params = Bundle().apply {
                                putString("has_barcode", (barcode != 0L).toString())
                                putBoolean("has_image", productPictureUrl.isNotEmpty())
                                putBoolean("is_first_product", isFirstProduct)
                                // Ne pas collecter les noms ou prix exacts
                            }
                            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("product_created", params)
                            productIdInserted
                        }
                    }

                    // Reset du flag après sauvegarde
                    wasScannedBarcode = false

                    // Afficher le message de succès standard
                    Toast.makeText(this@AddProductActivity, "Product saved with ID: $productId", Toast.LENGTH_SHORT).show()

                    // === NOUVEAU SYSTÈME DE GAMIFICATION SIMPLIFIÉ ===
                    if (isNewProduct) {
                        try {
                            val userId = getCurrentUserId()

                            if (isFirstProduct) {
                                // Premier produit jamais ajouté
                                gamificationManager.triggerEvent(userId, SimplifiedGamificationManager.EVENT_FIRST_PRODUCT_ADDED)

                                // Afficher la célébration spéciale pour le premier produit
                                showModernGamificationCelebration(userId, isFirstProduct = true)

                                // Marquer les félicitations comme affichées
                                firstProductManager.markCongratulationShown()

                                // Analytique pour la première félicitation
                                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("first_product_congratulation_shown", null)

                                Log.d("GAMIFICATION", "Premier produit ajouté - XP et achievement accordés")
                            } else {
                                // Produit standard
                                gamificationManager.triggerEvent(userId, SimplifiedGamificationManager.EVENT_PRODUCT_ADDED)
                                Log.d("GAMIFICATION", "Produit standard ajouté - XP accordé")
                            }

                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur dans le système de gamification: ${e.message}")
                            CrashlyticsManager.logException(e)

                            // Fallback vers l'ancien système si le nouveau échoue
                            if (isFirstProduct) {
                                showFirstProductCongratulation()
                                firstProductManager.markCongratulationShown()
                            }
                        }
                    }

                    updateResultIntentForTrackShopping(Product(productId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl))

                    finish()
                } catch (e: Exception) {
                    Log.d("SHOOPT_TAG", "Error saving product.", e)
                    Log.e("SHOOPT_FIRST_PRODUCT", "Erreur lors de la sauvegarde du produit", e)
                }
            }
        } else {
            displayFailedStorage()

            // Analytique anonymisee pour les champs manquants
            val params = Bundle().apply {
                putBoolean("missing_fields", true)
                // Sans detailler quels champs exactement sont manquants
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("product_save_validation_failed", params)
        }
    }

    /**
     * Obtient l'ID utilisateur actuel - à adapter selon votre système d'authentification
     */
    private fun getCurrentUserId(): String {
        // TODO: Adapter selon votre système d'authentification
        // Pour l'instant, utilise un ID basé sur l'appareil
        return android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "default_user"
    }

    /**
     * Affiche la célébration moderne de gamification - version simplifiée
     */
    private suspend fun showModernGamificationCelebration(userId: String, isFirstProduct: Boolean = false) {
        try {
            val userProfile = gamificationManager.getOrCreateUserProfile(userId)
            val xpProgressPercentage = gamificationManager.getUserXpProgressPercentage(userId)

            Log.d("GAMIFICATION", "Affichage célébration - XP: ${userProfile.totalXp}, Pourcentage: $xpProgressPercentage%")

            // Créer et ajouter la vue de célébration si elle n'existe pas
            if (gamificationCelebrationView == null) {
                gamificationCelebrationView = GamificationCelebrationView(this@AddProductActivity)
                val rootLayout = findViewById<ViewGroup>(android.R.id.content)
                rootLayout.addView(gamificationCelebrationView)
            }

            if (isFirstProduct) {
                // Célébration spéciale pour le premier produit
                gamificationCelebrationView?.showFirstProductCelebration(
                    userProfile = userProfile,
                    xpProgressPercentage = xpProgressPercentage
                ) {
                    // Callback quand la célébration est fermée
                    gamificationCelebrationView?.let { view ->
                        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
                        rootLayout.removeView(view)
                        gamificationCelebrationView = null
                    }
                }
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage de la célébration moderne: ${e.message}")
            CrashlyticsManager.logException(e)

            // Fallback vers l'ancien système
            showFirstProductCongratulation()
        }
    }

    /**
     * Vérifie et affiche les nouveaux achievements débloqués
     */
    private suspend fun checkAndShowNewAchievements(userId: String) {
        try {
            // Cette logique sera implémentée dans le GamificationManager
            // pour vérifier automatiquement les achievements lors des événements

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la vérification des achievements: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Affiche le dialog de felicitation moderne pour le premier produit ajoute
     */
    private fun showFirstProductCongratulation() {
        try {
            val congratulationDialog = FirstProductCongratulationDialog(this) {
                // Callback appele quand le dialog est ferme
                // On peut ici ajouter des actions supplementaires si necessaire
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("first_product_congratulation_dismissed", null)
            }

            congratulationDialog.show()

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage de la felicitation: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "show_first_product_congratulation")
            CrashlyticsManager.logException(e)

            // Fallback avec un toast simple en cas d'erreur
            Toast.makeText(this, getString(R.string.first_product_congrats_title), Toast.LENGTH_LONG).show()
        }
    }

    private fun saveShop(productShop: String) {
        if (!shopList.contains(productShop))
            addNewShop(productShop.trim())
    }

    private suspend fun analyzeProductImage(imageUrl: String) {
        try {
            // Analytique pour le debut de l'analyse d'image
            withContext(Dispatchers.Main) {
                val params = Bundle().apply {
                    putString("image_source", if (imageUrl.isEmpty()) "camera" else "url")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("image_analysis_started", params)
            }

            val apiKey = fetchHuggingFaceApiKey() ?: run {
                Log.e("DEBUG", getString(R.string.hf_key_empty))
                CrashlyticsManager.log("HF_KEY vide ou non disponible")
                CrashlyticsManager.setCustomKey("error_location", "api_key_missing")

                withContext(Dispatchers.Main) {
                    // Analytique pour l'erreur d'API key
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("image_analysis_api_key_missing", null)
                }
                return
            }

            val base64Image = getBase64EncodedImage()
            if (base64Image.isEmpty()) {
                CrashlyticsManager.log("Échec de l'encodage de l'image en Base64")
                CrashlyticsManager.setCustomKey("error_location", "image_encoding")

                withContext(Dispatchers.Main) {
                    // Analytique pour l'erreur d'encodage
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("image_analysis_encoding_failed", null)
                }
                return
            }

            val payload = createImageAnalysisPayload(base64Image)

            try {
                val response = sendImageAnalysisRequest(apiKey, payload)
                handleImageAnalysisResponse(response)

                withContext(Dispatchers.Main) {
                    // Analytique pour l'analyse reussie
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("image_analysis_success", null)
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'analyse de l'image: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "image_analysis_api")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddProductActivity, "Erreur d'analyse de l'image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()

                    // Analytique pour l'erreur d'analyse
                    val params = Bundle().apply {
                        putString("error_message", e.message ?: "Unknown error")
                        putString("error_type", e.javaClass.simpleName)
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("image_analysis_api_error", params)
                }
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur globale lors de l'analyse de l'image: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "image_analysis_process")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Log.e("DEBUG", "Error analyzing product image", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AddProductActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()

                // Analytique pour l'erreur globale
                val params = Bundle().apply {
                    putString("error_message", e.message ?: "Unknown error")
                    putString("error_type", e.javaClass.simpleName)
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("image_analysis_general_error", params)
            }
        }
    }

    private suspend fun fetchHuggingFaceApiKey(): String? {
        return suspendCancellableCoroutine { cont ->
            Firebase.remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val apiKey = Firebase.remoteConfig.getString("HF_KEY")
                    cont.resume(apiKey.ifEmpty { null })
                } else {
                    cont.resumeWithException(task.exception ?: Exception("Failed to fetch HF_KEY"))
                }
            }
        }
    }

    private fun createImageAnalysisPayload(base64Image: String): String {
        // Obtenir la langue actuelle de l'utilisateur (nom lisible, ex: "Français", "English")
        val userLanguage = Locale.getDefault().displayLanguage

        return  """
                    {
                        "messages": [
                            {
                                "role": "user",
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "Identify this product, its unit price, and its price per kilo in this image. Respond in JSON format with only 3 fields: name, unit_price, and kilo_price. Please reply in $userLanguage."
                                    },
                                    {
                                        "type": "image_url",
                                        "image_url": {
                                            "url": "data:image/jpeg;base64,$base64Image"
                                        }
                                    }
                                ]
                            }
                        ],
                        "model": "google/gemma-3-27b-it-fast",
                        "stream": false
                    }
                    """.trimIndent()
    }

    private suspend fun sendImageAnalysisRequest(apiKey: String, payload: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL("https://router.huggingface.co/nebius/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            connection.outputStream.use { it.write(payload.toByteArray()) }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Failed to analyze image. Response code: $responseCode, Error: $errorMessage")
            }
        }
    }

    private suspend fun handleImageAnalysisResponse(response: String) {
        val jsonResponse = JSONObject(response)
        val productDetails = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = productDetails.getString("content")
        val jsonContent = content
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val parsedDetails = JSONObject(jsonContent)

        withContext(Dispatchers.Main) {
            productNameEditText.setText(parsedDetails.getString("name"))
            productPriceEditText.setText(parsedDetails.getString("unit_price"))
            productUnitPriceEditText.setText(parsedDetails.getString("kilo_price"))
            // Auto-remplissage des champs termine - onboarding simplifie
        }
    }

    private fun getBase64EncodedImage(): String {
        return try {
            val bytes = productPictureFile.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("DEBUG", "Error encoding image to Base64", e)
            ""
        }
    }

    private fun getProductPictureData(): ByteArray {
        val (productPictureOrientation, productPictureBitmap) = getProductPictureBitmap(null)

        val productPictureByteArrayOutputStream = ByteArrayOutputStream()
        rotateProductPictureBitmap(
            productPictureOrientation,
            productPictureBitmap
        )?.compress(
            Bitmap.CompressFormat.JPEG,
            30,
            productPictureByteArrayOutputStream
        ) // compress image to 50% quality
        return productPictureByteArrayOutputStream.toByteArray()
    }

    private fun getScannedBarcode() {
        val scannedBarcode = intent.getStringExtra("barcode")
        if (scannedBarcode != null) productBarcodeEditText.setText(scannedBarcode)
    }

    private fun displayFailedStorage() {
        Toast.makeText(
            this@AddProductActivity,
            "Fail to add data because empty field found",
            Toast.LENGTH_SHORT
        )
            .show()
    }

    private fun getProductPictureBitmap(options: Options?): Pair<Int, Bitmap> {
        val exif = ExifInterface(productPictureFile.path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        return if (options != null) Pair(
            orientation,
            BitmapFactory.decodeFile(productPictureFile.path, options)
        )
        else Pair(
            orientation,
            BitmapFactory.decodeFile(productPictureFile.path)
        )
    }

    private fun rotateProductPictureBitmap(
        orientation: Int,
        bitmap: Bitmap
    ): Bitmap? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
            else -> { /* Do nothing */
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun displayProductPictureOnImageButton() {
        val options = Options()
        // Reduce downsampling for better quality - use 2 instead of 10
        options.inSampleSize = 2
        // Enable better quality scaling
        options.inPreferQualityOverSpeed = true

        val (productPictureOrientation, productPictureBitmap) = getProductPictureBitmap(options)

        // Change scale type to centerCrop when displaying a taken picture
        productPictureImageButton.scaleType = ImageView.ScaleType.CENTER_CROP

        val rotatedBitmap = rotateProductPictureBitmap(productPictureOrientation, productPictureBitmap)

        // Use Glide for better image loading and quality
        Glide.with(this)
            .load(rotatedBitmap)
            .override(560, 560) // Target size for the doubled container (280dp * 2 for density)
            .centerCrop()
            .into(productPictureImageButton)
    }

    private fun setMainVariables() {
        backImageButton = findViewById(R.id.back_IB)
        productPictureImageButton = findViewById(R.id.product_picture_IB)
        saveProductImageButton = findViewById(R.id.save_product_IB)
        productBarcodeEditText = findViewById(R.id.product_barcode_ET)
        productNameEditText = findViewById(R.id.product_name_ET)
        productPriceEditText = findViewById(R.id.product_price_ET)
        productUnitPriceEditText = findViewById(R.id.product_unit_price_ET)
        productShopAutoCompleteTextView = findViewById(R.id.shop_autocomplete)
        scanBarcodeButton = findViewById(R.id.scan_barcode_IB)

        // Configuration de l'AutoCompleteTextView pour le shop
        productShopAutoCompleteTextView.apply {
            setDropDownBackgroundResource(R.color.main_palette_isabelline)
            dropDownVerticalOffset = resources.getDimensionPixelSize(R.dimen.dropdown_vertical_offset)
            dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            threshold = 1

            // Forcer le scroll après l'apparition du clavier
            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        Handler(Looper.getMainLooper()).postDelayed({
                            scrollToShopField()
                        }, 300) // Delai pour laisser le clavier apparaître
                    }
                }
                false
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        scrollToShopField()
                    }, 300) // Delai pour laisser le clavier apparaître
                }
            }

            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    scrollToShopField()
                }
            })
        }

        // Attach generic scroll behaviour to other input fields on this screen
        fun attachScrollToField(editableView: View, parentLayoutId: Int) {
            val parentLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(parentLayoutId)

            // Focus change -> scroll
            editableView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        scrollToView(parentLayout)
                    }, 300)
                }
            }

            // Touch -> scroll after keyboard appears
            editableView.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        scrollToView(parentLayout)
                    }, 300)
                }
                false
            }
        }

        try {
            // barcode field
            attachScrollToField(productBarcodeEditText, R.id.barcode_layout)
            // product name
            attachScrollToField(productNameEditText, R.id.name_layout)
            // product price
            attachScrollToField(productPriceEditText, R.id.price_layout)
            // product unit price
            attachScrollToField(productUnitPriceEditText, R.id.unit_price_layout)
        } catch (e: Exception) {
            Log.w("AddProductActivity", "Impossible d'attacher le scroll aux champs: ${e.message}")
        }

        // Initialiser le switch d'autocompletion avec verification de securite
        val switchView = findViewById<SwitchMaterial>(R.id.autocompletion_switch)
        if (switchView != null) {
            autocompletionSwitch = switchView
            // Configurer le switch d'autocompletion
            setupAutocompletionSwitch()
        } else {
            CrashlyticsManager.log("Switch d'autocompletion non trouve dans le layout")
            CrashlyticsManager.setCustomKey("error_location", "autocompletion_switch_not_found")
        }
    }

    /**
     * Met à jour le texte de statut de l'autocompletion
     */
    private fun updateAutocompletionStatusText() {
        try {
            val statusTextView = findViewById<TextView>(R.id.autocompletion_status_text)
            val isAiEnabled = UserPreferences.isAiAutocompletionEnabled(this)
            val isMapsEnabled = UserPreferences.isMapsAutocompletionEnabled(this)

            val statusText = when {
                isAiEnabled && isMapsEnabled -> getString(R.string.autocompletion_toggle_description)
                isAiEnabled -> getString(R.string.ai_autocompletion_enabled)
                isMapsEnabled -> getString(R.string.maps_autocompletion_enabled)
                else -> getString(R.string.autocompletion_disabled_message)
            }

            statusTextView?.text = statusText
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la mise à jour du texte de statut: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "status_text_update")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Configure le switch d'autocompletion intelligente
     */
    private fun setupAutocompletionSwitch() {
        try {
            // Initialiser l'etat du switch base sur les preferences actuelles
            val isAiEnabled = UserPreferences.isAiAutocompletionEnabled(this)
            val isMapsEnabled = UserPreferences.isMapsAutocompletionEnabled(this)

            // Le switch est active si au moins une des deux fonctionnalites est activee
            autocompletionSwitch.isChecked = isAiEnabled || isMapsEnabled

            // Mettre à jour le texte de statut et l'apparence initiale
            updateAutocompletionStatusText()
            updateAutocompletionCardAppearance(autocompletionSwitch.isChecked)

            // Configurer le listener pour les changements d'etat
            autocompletionSwitch.setOnCheckedChangeListener { _, isChecked ->
                try {
                    // Enregistrer l'analytique du changement de preference
                    val params = Bundle().apply {
                        putBoolean("autocompletion_enabled", isChecked)
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("autocompletion_toggle_changed", params)

                    if (isChecked) {
                        // Activer les deux fonctionnalites quand le switch est active
                        UserPreferences.setAiAutocompletionEnabled(this, true)
                        UserPreferences.setMapsAutocompletionEnabled(this, true)

                        Toast.makeText(this, getString(R.string.ai_autocompletion) + " et " +
                                     getString(R.string.maps_autocompletion) + " activees",
                                     Toast.LENGTH_SHORT).show()
                    } else {
                        // Desactiver les deux fonctionnalites quand le switch est desactive
                        UserPreferences.setAiAutocompletionEnabled(this, false)
                        UserPreferences.setMapsAutocompletionEnabled(this, false)

                        Toast.makeText(this, getString(R.string.autocompletion_disabled_message),
                                     Toast.LENGTH_SHORT).show()
                    }

                    // Mettre à jour le texte de statut et l'apparence avec animation
                    updateAutocompletionStatusText()
                    updateAutocompletionCardAppearance(isChecked)

                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du changement d'etat du switch d'autocompletion: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "autocompletion_switch_change")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)
                }
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du switch d'autocompletion: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "autocompletion_switch_setup")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
        }
    }

    /**
     * Met à jour l'apparence du card d'autocompletion avec une animation fluide
     */
    private fun updateAutocompletionCardAppearance(isEnabled: Boolean) {
        try {
            val autocompletionCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.autocompletion_toggle_card)
            val statusTextView = findViewById<TextView>(R.id.autocompletion_status_text)

            // Chercher l'icône dans le layout LinearLayout du card
            val iconView = autocompletionCard?.let { card ->
                val linearLayout = card.getChildAt(0) as? android.widget.LinearLayout
                linearLayout?.findViewById<ImageView>(android.R.id.icon)
                    ?: linearLayout?.getChildAt(0) as? ImageView // Premier ImageView dans le LinearLayout
            }

            // Animation de transition fluide
            val animationDuration = 300L

            // Couleurs pour les differents etats
            val enabledCardColor = androidx.core.content.ContextCompat.getColor(this, R.color.main_palette_isabelline)
            val disabledCardColor = androidx.core.content.ContextCompat.getColor(this, R.color.main_palette_old_lavender)
            val enabledTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.main_palette_old_lavender_variant)
            val disabledTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.white)
            val enabledStrokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.main_palette_old_lavender_variant)
            val disabledStrokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.main_palette_old_lavender_variant)

            if (isEnabled) {
                // État active : couleurs normales
                autocompletionCard?.animate()
                    ?.alpha(1.0f)
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.setDuration(animationDuration)
                    ?.withStartAction {
                        autocompletionCard.setCardBackgroundColor(enabledCardColor)
                        autocompletionCard.strokeColor = enabledStrokeColor
                        autocompletionCard.strokeWidth = 2
                        statusTextView?.setTextColor(enabledTextColor)
                    }
                    ?.start()
            } else {
                // État desactive : couleurs attenuees avec effet visuel
                autocompletionCard?.animate()
                    ?.alpha(0.7f)
                    ?.scaleX(0.98f)
                    ?.scaleY(0.98f)
                    ?.setDuration(animationDuration)
                    ?.withStartAction {
                        autocompletionCard.setCardBackgroundColor(disabledCardColor)
                        autocompletionCard.strokeColor = disabledStrokeColor
                        autocompletionCard.strokeWidth = 1
                        statusTextView?.setTextColor(disabledTextColor)
                    }
                    ?.start()
            }

            // Animation de l'icône si presente
            iconView?.let { icon ->
                val iconColor = if (isEnabled) enabledTextColor else disabledTextColor
                icon.animate()
                    .rotation(if (isEnabled) 0f else -5f)
                    .setDuration(animationDuration)
                    .withStartAction {
                        icon.setColorFilter(iconColor)
                    }
                    .start()
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la mise à jour de l'apparence du card: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "card_appearance_update")
            CrashlyticsManager.logException(e)
        }
    }

    private fun retrieveMainData(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            productPictureImageButton.setImageBitmap(savedInstanceState?.getParcelable<Bitmap>("productPicture"))
            productBarcodeEditText.setText(savedInstanceState.getString("productBarCode"))
            productNameEditText.setText(savedInstanceState.getString("productName"))
            productPriceEditText.setText(savedInstanceState.getString("productPrice"))
            productUnitPriceEditText.setText(savedInstanceState.getString("productUnitPrice"))
            productShopAutoCompleteTextView.setText(savedInstanceState.getString("productShop"))
        }
    }

    private fun retrieveProductDataFromIntent() {
        val productId = intent.getStringExtra("productId")
        val barcode = intent.getLongExtra("barcode", 0L)
        val name = intent.getStringExtra("name")
        val shop = intent.getStringExtra("shop")
        val price = intent.getDoubleExtra("price", 0.0)
        val unitPrice = intent.getDoubleExtra("unitPrice", 0.0)
        val pictureUrl = intent.getStringExtra("pictureUrl")

        if (productId != null) {
            retrievedProductId = productId
        } else {

        }

        productBarcodeEditText.setText(if (barcode != 0L) barcode.toString() else "")
        productNameEditText.setText(name ?: "")
        productShopAutoCompleteTextView.setText(shop ?: "")
        productPriceEditText.setText(if (price != 0.0) price.toString() else "")
        productUnitPriceEditText.setText(if (unitPrice != 0.0) unitPrice.toString() else "")

        if (!pictureUrl.isNullOrEmpty()) {
            // Set scale type to centerCrop for consistent image display
            productPictureImageButton.scaleType = ImageView.ScaleType.CENTER_CROP

            // Use consistent sizing with displayProductPictureOnImageButton method
            Glide.with(this)
                .load(pictureUrl)
                .override(560, 560) // Target size for the doubled container (280dp * 2 for density)
                .centerCrop()
                .into(productPictureImageButton)
        } else {
            Log.w("AddProductActivity", "Missing pictureUrl")
        }
    }

    private fun updateResultIntentForTrackShopping(product: Product) {
        val source = intent.getStringExtra("source")

        if (source != null && source == "TrackShoppingActivity") {
            val resultIntent = Intent()
            resultIntent.putExtra("productToAddToShoppingCart", product)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private suspend fun saveProductImage(imageData: ByteArray, pathString: String): String? {
        return imageStorage.uploadImage(imageData, pathString)
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    // Ajout du callback pour la permission de localisation
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && ::placesClient.isInitialized) {
            // Relance la recuperation de la localisation
            CoroutineScope(Dispatchers.Main).launch {
                fetchCurrentLocationAndFillShopName()
            }
        } else {
            Toast.makeText(this, "Permission localisation refusee ou Places non initialise", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchCurrentLocationAndFillShopName() {
        if (!checkLocationPermission()) {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (!::placesClient.isInitialized) {
            Log.e("DEBUG", "placesClient is not initialized. Skipping location fetch.")
            return
        }
        val location = suspendCancellableCoroutine<Location?> { cont ->
            fusedLocationClient.lastLocation.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        if (location != null) {
            val placeRequest = FindCurrentPlaceRequest.newInstance(
                listOf(Place.Field.NAME, Place.Field.ADDRESS)
            )
            val response = suspendCancellableCoroutine { cont ->
                placesClient.findCurrentPlace(placeRequest)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val mostLikelyPlace = response.placeLikelihoods.maxByOrNull { it.likelihood }
            val shopName = mostLikelyPlace?.place?.name ?: getString(R.string.unknown_shop)
            productShopAutoCompleteTextView.setText(shopName)
        } else {
            productShopAutoCompleteTextView.setText(getString(R.string.unknown_shop))
        }
    }

    private fun showLoadingOverlay() {
        val loadingOverlay = findViewById<ImageView>(R.id.loading_overlay_image)
        val rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate)
        loadingOverlay.startAnimation(rotateAnimation)
        findViewById<View>(R.id.loading_overlay).visibility = View.VISIBLE
        startDotAnimation() // Start the dot animation when showing the overlay
    }

    private fun hideLoadingOverlay() {
        stopDotAnimation() // Stop the dot animation when hiding the overlay
        findViewById<View>(R.id.loading_overlay).visibility = View.GONE
        val loadingOverlay = findViewById<ImageView>(R.id.loading_overlay_image)
        loadingOverlay.clearAnimation()
    }

    private fun startDotAnimation() {
        if (isAnimatingDots) return
        isAnimatingDots = true
        dotHandler.post(object : Runnable {
            override fun run() {
                val loadingMessageTextView = findViewById<TextView>(R.id.loading_overlay_message)
                val baseMessage = loadingMessages.joinToString("\n")
                val dots = "...".take(dotCount % 4) // Add 0 to 3 dots
                val paddedDots = dots.padEnd(3, ' ') // Ensure the width remains constant
                loadingMessageTextView.text = "$baseMessage$paddedDots" // Append padded dots
                dotCount++
                dotHandler.postDelayed(this, 300) // Update every 300ms
            }
        })
    }

    private fun stopDotAnimation() {
        isAnimatingDots = false
        dotHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        // Guide utilisateur supprime pour simplifier l'onboarding
    }

    /**
     * Configure et demarre le tour de spotlight pour guider l'utilisateur
     * dans l'ajout d'un produit avec toutes les fonctionnalites disponibles
     */
    private fun setupSpotlightTour() {
        try {
            // Verifier si le spotlight doit être affiche
            if (!isSpotlightAvailable()) {
                return
            }

            // Creer la liste des elements à mettre en surbrillance
            val spotlightItems = mutableListOf<com.dedoware.shoopt.models.SpotlightItem>()

            // Spotlight pour la photo du produit
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productPictureImageButton,
                    titleRes = R.string.spotlight_add_photo_title,
                    descriptionRes = R.string.spotlight_add_photo_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Spotlight pour le champ code-barres
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productBarcodeEditText,
                    titleRes = R.string.spotlight_add_barcode_title,
                    descriptionRes = R.string.spotlight_add_barcode_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le bouton de scan de code-barres
            spotlightItems.add(
                createSpotlightItem(
                    targetView = scanBarcodeButton,
                    titleRes = R.string.spotlight_add_scan_button_title,
                    descriptionRes = R.string.spotlight_add_scan_button_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Spotlight pour le nom du produit
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productNameEditText,
                    titleRes = R.string.spotlight_add_name_title,
                    descriptionRes = R.string.spotlight_add_name_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le prix
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productPriceEditText,
                    titleRes = R.string.spotlight_add_price_title,
                    descriptionRes = R.string.spotlight_add_price_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le prix unitaire
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productUnitPriceEditText,
                    titleRes = R.string.spotlight_add_unit_price_title,
                    descriptionRes = R.string.spotlight_add_unit_price_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le magasin
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productShopAutoCompleteTextView,
                    titleRes = R.string.spotlight_add_shop_title,
                    descriptionRes = R.string.spotlight_add_shop_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le bouton de sauvegarde
            spotlightItems.add(
                createSpotlightItem(
                    targetView = saveProductImageButton,
                    titleRes = R.string.spotlight_add_save_title,
                    descriptionRes = R.string.spotlight_add_save_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Demarrer le tour de spotlight avec un leger delai pour que l'interface soit prête
            window.decorView.post {
                startSpotlightTour(spotlightItems) {
                    // Callback appele à la fin du tour
                    val spParams = Bundle().apply {
                        putString("action", "spotlight_tour_completed")
                        putString("category", "onboarding")
                        putString("screen", "AddProductActivity")
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", spParams)
                }
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du spotlight: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "setup_spotlight_tour_add_product")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: mettre à jour l'intent de l'activite

        // Verifier si on doit forcer un refresh des spotlights
        val forceRefresh = intent?.getBooleanExtra("force_spotlight_refresh", false) ?: false
        if (forceRefresh) {
            CrashlyticsManager.log("AddProductActivity onNewIntent: Force spotlight refresh requested")

            // Verifier si l'onboarding est complete et forcer les spotlights
            if (UserPreferences.isOnboardingCompleted(this)) {
                // Delai court pour laisser l'interface se stabiliser
                window.decorView.postDelayed({
                    setupSpotlightTour()
                }, 500)
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private fun scrollToShopField() {
        // Delegate to generic view scroller for the shop layout
        val shopLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.shop_layout)
        scrollToView(shopLayout)
    }

    /**
     * Scroll generic helper: scroll the main NestedScrollView so that targetView is visible
     * with an extra margin to account for the on-screen keyboard.
     */
    private fun scrollToView(targetView: View) {
        val scrollView = findViewById<NestedScrollView>(R.id.global_add_or_update_product_view)

        targetView.post {
            try {
                val scrollViewRect = Rect()
                val targetRect = Rect()

                scrollView.getGlobalVisibleRect(scrollViewRect)
                targetView.getGlobalVisibleRect(targetRect)

                // Estimation de la hauteur du clavier et marge supplémentaire
                val keyboardEstimate = resources.displayMetrics.heightPixels / 2
                val marginPx = dpToPx(150)

                val targetScroll = (targetRect.top - scrollViewRect.top) +
                        scrollView.scrollY -
                        marginPx

                scrollView.smoothScrollTo(0, targetScroll.coerceAtLeast(0))
                scrollView.requestLayout()
            } catch (e: Exception) {
                Log.e("AddProductActivity", "Erreur lors du scroll dynamique: ${e.message}")
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
