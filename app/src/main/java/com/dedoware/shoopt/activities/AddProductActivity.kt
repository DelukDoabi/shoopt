package com.dedoware.shoopt.activities

import android.app.Activity
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
import com.dedoware.shoopt.utils.ShooptUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
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
    private lateinit var backImageButton: ImageButton
    private lateinit var productPictureImageButton: ImageButton
    private lateinit var saveProductImageButton: ImageButton
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
            if (result.resultCode == Activity.RESULT_OK) {
                displayProductPictureOnImageButton()

                val pictureUrl = intent.getStringExtra("pictureUrl") ?: ""

                // Trigger image analysis and shop name auto-completion in parallel
                CoroutineScope(Dispatchers.Main).launch {
                    showLoadingOverlay()
                    val imageAnalysisJob = async {
                        analyzeProductImageWithMessage(pictureUrl)
                    }

                    val shopNameAutoCompleteJob = async {
                        fetchCurrentLocationAndFillShopNameWithMessage()
                    }

                    // Wait for both tasks to complete
                    imageAnalysisJob.await()
                    shopNameAutoCompleteJob.await()
                    hideLoadingOverlay()
                }
            }
        }

    private var analyzeImageJob: Job? = null

    private lateinit var loadingOverlay: View

    private val loadingMessages = mutableListOf<String>()
    private val loadingMessagesMutex = Mutex()

    private var dotCount = 0
    private var isAnimatingDots = false
    private val dotHandler = Handler(Looper.getMainLooper())

    private suspend fun updateLoadingMessage(message: String, add: Boolean) {
        loadingMessagesMutex.withLock {
            if (add) {
                loadingMessages.add(message)
            } else {
                loadingMessages.remove(message)
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

        setContentView(R.layout.activity_add_product)

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

        imageStorage = if (useFirebase) {
            FirebaseImageStorage()
        } else {
            LocalImageStorage(this)
        }

        supportActionBar?.hide()

        setMainVariables()

        retrieveProductDataFromIntent()

        retrieveMainData(savedInstanceState)

        if (useFirebase) {
            ShooptUtils.doAfterInitFirebase(baseContext) { setShopsData() }
        } else {
            setShopsData()
        }

        productPictureFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "temp_shoopt_product_picture.jpg"
        )

        getScannedBarcode()

        backImageButton.setOnClickListener {
            finish()
        }

        productPictureImageButton.setOnClickListener {
            launchCamera()
        }

        saveProductImageButton.setOnClickListener {
            if (intent.hasExtra("productId")) {
                val pictureUrl = intent.getStringExtra("pictureUrl")
                val price = productPriceEditText.text.toString()
                val unitPrice = productUnitPriceEditText.text.toString()

                if (!price.isNullOrEmpty() && !unitPrice.isNullOrEmpty()) {
                    pictureUrl?.let { saveProduct(it, price, unitPrice) }
                } else {
                    // Handle missing price or unit price
                    Toast.makeText(this, "Price and Unit Price are required", Toast.LENGTH_SHORT).show()
                }
            } else {
                saveAllProductData()
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Places API
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.fetch(0).addOnCompleteListener { fetchTask ->
            if (fetchTask.isSuccessful) {
                remoteConfig.activate().addOnCompleteListener { activateTask ->
                    if (activateTask.isSuccessful) {
                        Log.d("DEBUG", "Remote Config fetch and activate successful.")
                        val mapsApiKey = remoteConfig.getString("MAPS_KEY")
                        Log.d("DEBUG", "Fetched MAPS_KEY: $mapsApiKey")

                        if (mapsApiKey.isNotEmpty()) {
                            Places.initialize(applicationContext, mapsApiKey)
                            placesClient = Places.createClient(this)
                        } else {
                            Log.e("DEBUG", "MAPS_KEY is empty. Places API initialization skipped.")
                        }
                    } else {
                        Log.e("DEBUG", "Remote Config activation failed.", activateTask.exception)
                    }
                }
            } else {
                Log.e("DEBUG", "Remote Config fetch failed.", fetchTask.exception)
            }

            if (!::placesClient.isInitialized) {
                Log.e("DEBUG", "placesClient is not initialized. Skipping location fetch.")
                return@addOnCompleteListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                fetchCurrentLocationAndFillShopName()
            }
        }

        // Inflate and configure the loading overlay
        val inflater = LayoutInflater.from(this)
        loadingOverlay = inflater.inflate(R.layout.loading_overlay, null)
        loadingOverlay.visibility = View.GONE
        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        loadingOverlay = inflater.inflate(R.layout.loading_overlay, rootLayout, false)
        rootLayout.addView(loadingOverlay)
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
                    "Failed to load shops: ${e.localizedMessage}",
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
                    Toast.makeText(this@AddProductActivity, "New shop added successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AddProductActivity, "Failed to add shop.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.d("SHOOPT_TAG", "Failed to add shop.", e)
                Toast.makeText(this@AddProductActivity, "Error adding shop: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val productPictureUri =
            FileProvider.getUriForFile(this, "com.dedoware.shoopt.fileprovider", productPictureFile)

        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, productPictureUri)

        resultLauncher.launch(cameraIntent)
    }

    private fun sanitizePriceInput(price: String): String {
        return price.replace(",", ".").replace(Regex("[^0-9.]"), "")
    }

    // Update the saveAllProductData function to sanitize price inputs
    private fun saveAllProductData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val productBarcode = productBarcodeEditText.text.toString().toLong()
                val productName = productNameEditText.text.toString()
                val productShop = productShopAutoCompleteTextView.text.toString()

                // Sanitize price inputs
                val sanitizedPrice = sanitizePriceInput(productPriceEditText.text.toString())
                val sanitizedUnitPrice = sanitizePriceInput(productUnitPriceEditText.text.toString())

                val pictureUrl = saveProductImage(getProductPictureData(), "product-pictures/$productBarcode-$productName-$productShop.jpg")

                if (pictureUrl != null) {
                    saveShop(productShop)

                    saveProduct(pictureUrl, sanitizedPrice, sanitizedUnitPrice)
                } else {
                    Toast.makeText(this@AddProductActivity, "Failed to upload product picture.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.d("SHOOPT_TAG", "Error saving product.", e)
                Toast.makeText(this@AddProductActivity, "Error saving product: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Update the saveProduct function to accept sanitized prices
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
                            product.id
                        } else {
                            var repositoryProductId = productRepository.getUniqueId()

                            if (repositoryProductId == null) {
                                repositoryProductId = UUID.randomUUID().toString()
                            }

                            val product = Product(repositoryProductId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl)
                            productRepository.insert(product)
                        }
                    }

                    Toast.makeText(this@AddProductActivity, "Product saved with ID: $productId", Toast.LENGTH_SHORT).show()

                    updateResultIntentForTrackShopping(Product(productId, barcode, timestamp, name, price.toDouble(), unitPrice.toDouble(), shop, productPictureUrl))

                    finish()
                } catch (e: Exception) {
                    Log.d("SHOOPT_TAG", "Error saving product.", e)
                    Toast.makeText(this@AddProductActivity, "Error saving product: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            displayFailedStorage()
        }
    }

    private fun saveShop(productShop: String) {
        if (!shopList.contains(productShop))
            addNewShop(productShop.trim())
    }

    // Replace analyzeProductImage to directly perform the curl equivalent
    private suspend fun analyzeProductImage(imageUrl: String) {
        try {
            val apiKey = fetchHuggingFaceApiKey() ?: run {
                Log.e("DEBUG", getString(R.string.hf_key_empty))
                return
            }

            val base64Image = getBase64EncodedImage()
            val payload = createImageAnalysisPayload(base64Image)

            val response = sendImageAnalysisRequest(apiKey, payload)
            handleImageAnalysisResponse(response)
        } catch (e: Exception) {
            Log.e("DEBUG", "Error analyzing product image", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AddProductActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
        options.inSampleSize = 10

        val (productPictureOrientation, productPictureBitmap) = getProductPictureBitmap(
            options
        )

        productPictureImageButton.setImageBitmap(
            rotateProductPictureBitmap(
                productPictureOrientation,
                productPictureBitmap
            )
        )
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

        if (productId != null && barcode != 0L && name != null && shop != null && price != 0.0 && unitPrice != 0.0) {
            retrievedProductId = productId
            productBarcodeEditText.setText(barcode.toString())
            productNameEditText.setText(name)
            productShopAutoCompleteTextView.setText(shop)
            productPriceEditText.setText(price.toString())
            productUnitPriceEditText.setText(unitPrice.toString())

            // Load the product picture from the pictureUrl using Glide with resizing options
            Glide.with(this)
                .load(pictureUrl)
                .override(300, 300)
                .into(productPictureImageButton)
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

    private suspend fun fetchCurrentLocationAndFillShopName() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        val location = suspendCancellableCoroutine<Location?> { cont ->
            fusedLocationClient.lastLocation.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        if (location != null && ::placesClient.isInitialized) {
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
                val dots = ".".repeat(dotCount % 4) // Add 0 to 3 dots
                loadingMessageTextView.text = baseMessage + dots
                dotCount++
                dotHandler.postDelayed(this, 300) // Update every 500ms
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
