package com.dedoware.shoopt.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID


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

        ShooptUtils.doAfterInitFirebase(baseContext) { setShopsData() }

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
            if (intent.hasExtra("productId"))
                intent.getStringExtra("pictureUrl")
                    ?.let { it -> saveProduct(it) }
            else
                saveAllProductData()
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

    private fun saveAllProductData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val productBarcode = productBarcodeEditText.text.toString().toLong()
                val productName = productNameEditText.text.toString()
                val productShop = productShopAutoCompleteTextView.text.toString()

                val pictureUrl = saveProductImage(getProductPictureData(), "product-pictures/$productBarcode-$productName-$productShop.jpg")

                if (pictureUrl != null) {
                    saveShop(productShop)

                    saveProduct(pictureUrl)
                } else {
                    Toast.makeText(this@AddProductActivity, "Failed to upload product picture.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.d("SHOOPT_TAG", "Error saving product.", e)
                Toast.makeText(this@AddProductActivity, "Error saving product: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProduct(productPictureUrl: String) {
        val barcode = if (productBarcodeEditText.text.toString()
                .isEmpty()
        ) "0".toLong() else productBarcodeEditText.text.toString().toLong()
        val timestamp = System.currentTimeMillis()
        val name = productNameEditText.text.toString()
        val price = productPriceEditText.text.toString().toDouble()
        val unitPrice = productUnitPriceEditText.text.toString().toDouble()
        val shop = productShopAutoCompleteTextView.text.toString()

        if (name.isNotEmpty() && !price.isNaN() && !unitPrice.isNaN() && shop.isNotEmpty()) {

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val productId = withContext(Dispatchers.IO) {
                        if (intent.hasExtra("productId")) {
                            val product = Product(retrievedProductId, barcode, timestamp, name, price, unitPrice, shop, productPictureUrl)
                            productRepository.update(product)
                            product.id
                        } else {
                            var repositoryProductId = productRepository.getUniqueId()

                            if (repositoryProductId == null) {
                                repositoryProductId = UUID.randomUUID().toString()
                            }

                            val product = Product(repositoryProductId, barcode, timestamp, name, price, unitPrice, shop, productPictureUrl)
                            productRepository.insert(product)
                        }
                    }

                    Toast.makeText(this@AddProductActivity, "Product saved with ID: $productId", Toast.LENGTH_SHORT).show()

                    updateResultIntentForTrackShopping(Product(productId, barcode, timestamp, name, price, unitPrice, shop, productPictureUrl))

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

        productPictureBitmap.recycle()
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
            productPictureImageButton.setImageBitmap(savedInstanceState.getParcelable("productPicture"))
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
}