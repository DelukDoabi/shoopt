package com.dedoware.shoopt.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Matrix
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
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
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.ShooptUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.button.MaterialButton
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


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
    val shopList = mutableListOf<String>()
    private lateinit var productRepository: IProductRepository
    private lateinit var imageStorage: IImageStorage
    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }
    private val useFirebase = false // This could be a config or user preference

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == Activity.RESULT_OK) {
                    displayProductPictureOnImageButton()

                    val pictureUrl = intent.getStringExtra("pictureUrl") ?: ""

                    // Trigger image analysis and shop name auto-completion in parallel
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            showLoadingOverlay()
                            val imageAnalysisJob = async {
                                analyzeProductImageWithMessage(pictureUrl)
                            }

                            val shopNameAutoCompleteJob = async {
                                fetchCurrentLocationAndFillShopNameWithMessage()
                            }

                            // Wait for both tasks to complete
                            try {
                                imageAnalysisJob.await()
                                shopNameAutoCompleteJob.await()
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
                            CrashlyticsManager.log("Erreur lors de l'analyse de l'image ou de la récupération de l'emplacement: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.setCustomKey("error_location", "product_analysis")
                            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                            CrashlyticsManager.logException(e)

                            hideLoadingOverlay()
                        }
                    }
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur dans resultLauncher: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "result_launcher")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

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

            // Enregistrer l'événement d'ouverture de l'écran AddProduct
            AnalyticsManager.logScreenView("AddProduct", "AddProductActivity")

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
                CrashlyticsManager.log("Erreur lors de la récupération des données du produit depuis l'intent: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "intent_data_retrieval")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            try {
                retrieveMainData(savedInstanceState)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la récupération des données sauvegardées: ${e.message ?: "Message non disponible"}")
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
                    CrashlyticsManager.log("Erreur lors de l'initialisation des données de magasins: ${e.message ?: "Message non disponible"}")
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
                CrashlyticsManager.log("Erreur lors de la création du fichier image temporaire: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "temp_file_creation")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            try {
                getScannedBarcode()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la récupération du code-barres scanné: ${e.message ?: "Message non disponible"}")
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
                    CrashlyticsManager.log("Erreur lors du lancement de la caméra: ${e.message ?: "Message non disponible"}")
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
                                    CrashlyticsManager.log("MAPS_KEY vide, initialisation de Places API ignorée")
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
                            CrashlyticsManager.log("Échec de la récupération de Remote Config: ${it.message ?: "Message non disponible"}")
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
                            CrashlyticsManager.log("Erreur lors de la récupération de la localisation: ${e.message ?: "Message non disponible"}")
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
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans onCreate d'AddProductActivity: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "add_product_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            // Affichage d'un message d'erreur à l'utilisateur
            Toast.makeText(this, "Une erreur est survenue lors du démarrage de l'application. Veuillez réessayer.", Toast.LENGTH_LONG).show()

            // Fermer l'activité en cas d'erreur critique
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

        productShopAutoCompleteTextView.setOnClickListener {
            productShopAutoCompleteTextView.showDropDown()
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
            launchCameraInternal()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCameraInternal() {
        // Analytique pour le lancement de la caméra
        AnalyticsManager.logSelectContent("product_picture", "camera", "product_capture")

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val productPictureUri =
            FileProvider.getUriForFile(this, "com.dedoware.shoopt.fileprovider", productPictureFile)

        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, productPictureUri)

        resultLauncher.launch(cameraIntent)
    }

    private fun launchBarcodeScanner() {
        // Analytique pour le scan de code-barres
        AnalyticsManager.logSelectContent("barcode_scan", "scanner", "product_barcode")

        try {
            // Utilisation de notre nouvelle implémentation basée sur ML Kit
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

                // Analytique pour le code-barres scanné
                val params = Bundle().apply {
                    putString("barcode_length", barcodeValue.length.toString())
                }
                AnalyticsManager.logCustomEvent("barcode_scanned", params)
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_LONG).show()
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

    // Update the saveProduct function without collecting personal data
    private fun saveProduct(productPictureUrl: String, price: String, unitPrice: String) {
        val barcode = if (productBarcodeEditText.text.toString().isEmpty()) "0".toLong() else productBarcodeEditText.text.toString().toLong()
        val timestamp = System.currentTimeMillis()
        val name = productNameEditText.text.toString()
        val shop = productShopAutoCompleteTextView.text.toString()

        if (name.isNotEmpty() && price.isNotEmpty() && unitPrice.isNotEmpty() && shop.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val productId = withContext(Dispatchers.IO) {
                        if (intent.hasExtra("productId")) {
                            val product = Product(retrievedProductId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl)
                            productRepository.update(product)

                            // Analytique anonymisée pour la mise à jour de produit
                            val params = Bundle().apply {
                                putString("has_barcode", (barcode != 0L).toString())
                                // Ne pas collecter le nom du produit ou autres données personnelles
                            }
                            AnalyticsManager.logCustomEvent("product_updated", params)

                            product.id
                        } else {
                            var repositoryProductId = productRepository.getUniqueId()

                            if (repositoryProductId == null) {
                                repositoryProductId = UUID.randomUUID().toString()
                            }

                            val product = Product(repositoryProductId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl)
                            val productIdInserted = productRepository.insert(product)

                            // Analytique anonymisée pour l'ajout de produit
                            val params = Bundle().apply {
                                putString("has_barcode", (barcode != 0L).toString())
                                putBoolean("has_image", productPictureUrl.isNotEmpty())
                                // Ne pas collecter les noms ou prix exacts
                            }
                            AnalyticsManager.logCustomEvent("product_created", params)
                            productIdInserted
                        }
                    }

                    Toast.makeText(this@AddProductActivity, "Product saved with ID: $productId", Toast.LENGTH_SHORT).show()

                    updateResultIntentForTrackShopping(Product(productId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl))

                    finish()
                } catch (e: Exception) {
                    Log.d("SHOOPT_TAG", "Error saving product.", e)
                    Toast.makeText(this@AddProductActivity, "Error saving product: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()

                    // Analytique anonymisée pour les erreurs de sauvegarde
                    val params = Bundle().apply {
                        putString("error_type", e.javaClass.simpleName)
                        // Sans les détails du message d'erreur qui pourrait contenir des infos sensibles
                    }
                    AnalyticsManager.logCustomEvent("product_save_error", params)
                }
            }
        } else {
            displayFailedStorage()

            // Analytique anonymisée pour les champs manquants
            val params = Bundle().apply {
                putBoolean("missing_fields", true)
                // Sans détailler quels champs exactement sont manquants
            }
            AnalyticsManager.logCustomEvent("product_save_validation_failed", params)
        }
    }

    private fun saveShop(productShop: String) {
        if (!shopList.contains(productShop))
            addNewShop(productShop.trim())
    }

    // Mise à jour de la méthode pour inclure l'analytique lors de l'analyse d'image
    private suspend fun analyzeProductImage(imageUrl: String) {
        try {
            // Analytique pour le début de l'analyse d'image
            withContext(Dispatchers.Main) {
                val params = Bundle().apply {
                    putString("image_source", if (imageUrl.isEmpty()) "camera" else "url")
                }
                AnalyticsManager.logCustomEvent("image_analysis_started", params)
            }

            val apiKey = fetchHuggingFaceApiKey() ?: run {
                Log.e("DEBUG", getString(R.string.hf_key_empty))
                CrashlyticsManager.log("HF_KEY vide ou non disponible")
                CrashlyticsManager.setCustomKey("error_location", "api_key_missing")

                withContext(Dispatchers.Main) {
                    // Analytique pour l'erreur d'API key
                    AnalyticsManager.logCustomEvent("image_analysis_api_key_missing", null)
                }
                return
            }

            val base64Image = getBase64EncodedImage()
            if (base64Image.isEmpty()) {
                CrashlyticsManager.log("Échec de l'encodage de l'image en Base64")
                CrashlyticsManager.setCustomKey("error_location", "image_encoding")

                withContext(Dispatchers.Main) {
                    // Analytique pour l'erreur d'encodage
                    AnalyticsManager.logCustomEvent("image_analysis_encoding_failed", null)
                }
                return
            }

            val payload = createImageAnalysisPayload(base64Image)

            try {
                val response = sendImageAnalysisRequest(apiKey, payload)
                handleImageAnalysisResponse(response)

                withContext(Dispatchers.Main) {
                    // Analytique pour l'analyse réussie
                    AnalyticsManager.logCustomEvent("image_analysis_success", null)
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
                    AnalyticsManager.logCustomEvent("image_analysis_api_error", params)
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
                AnalyticsManager.logCustomEvent("image_analysis_general_error", params)
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
        return  """
                    {
                        "messages": [
                            {
                                "role": "user",
                                "content": [
                                    {
                                        "type": "text",
                                        "text": "Identify this product, its unit price, and its price per kilo in this image. Respond in JSON format with only 3 fields: name, unit_price, and kilo_price."
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
            Log.w("AddProductActivity", "Missing productId")
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

    private suspend fun removeProductImage(imageUrl: String): Boolean {
        return imageStorage.deleteImage(imageUrl)
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
            // Relance la récupération de la localisation
            CoroutineScope(Dispatchers.Main).launch {
                fetchCurrentLocationAndFillShopName()
            }
        } else {
            Toast.makeText(this, "Permission localisation refusée ou Places non initialisé", Toast.LENGTH_SHORT).show()
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

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
