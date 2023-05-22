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
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop
import com.dedoware.shoopt.utils.ShooptUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream
import java.io.File


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


    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                displayProductPictureOnImageButton()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_product)

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
                    ?.let { it -> saveProductInFirebaseDatabase(it) }
            else
                saveAllProductDataInFirebase()
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

        val shopsReference = ShooptUtils.getFirebaseDatabaseReference().child("shops")

        shopsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (!dataSnapshot.exists()) {
                    shopsReference.setValue(HashMap<String, Any>())
                }
                dataSnapshot.children.forEach { shop ->
                    val shopName = shop.child("name").getValue(String::class.java)
                    if (shopName != null && !shopList.contains(shopName)) {
                        shopList.add(shopName)
                    }
                }
                adapter.clear()
                adapter.addAll(shopList)
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.d("SHOOPT_TAG", "Failed to read value.", databaseError.toException())
                Toast.makeText(
                    this@AddProductActivity,
                    databaseError.toException().localizedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        productShopAutoCompleteTextView.setOnClickListener {
            productShopAutoCompleteTextView.showDropDown()
        }
    }

    private fun addNewShop(shopName: String) {
        val shopsReference = ShooptUtils.getFirebaseDatabaseReference().child("shops")

        shopsReference.orderByChild("name").equalTo(shopName)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (!dataSnapshot.exists()) {
                        val newShop = Shop(shopName)
                        shopsReference.push().setValue(newShop) { error, _ ->
                            if (error == null) {
                                Log.d("SHOOPT_TAG", "New shop added successfully!")
                                Toast.makeText(
                                    this@AddProductActivity,
                                    "New shop added successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Log.d("SHOOPT_TAG", error.message)
                                Toast.makeText(
                                    this@AddProductActivity,
                                    error.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(
                            this@AddProductActivity,
                            "Shop already exists!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.d("SHOOPT_TAG", databaseError.details)
                    Toast.makeText(
                        this@AddProductActivity,
                        "Error adding shop: ${databaseError.details}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val productPictureUri =
            FileProvider.getUriForFile(this, "com.dedoware.shoopt.fileprovider", productPictureFile)

        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, productPictureUri)

        resultLauncher.launch(cameraIntent)
    }

    private fun saveAllProductDataInFirebase() {
        val storageRef = Firebase.storage.reference

        val productBarcode = productBarcodeEditText.text.toString().toLong()
        val productName = productNameEditText.text.toString()
        val productShop = productShopAutoCompleteTextView.text.toString()

        val productPicturesRef =
            storageRef.child("product-pictures/$productBarcode-$productName-$productShop.jpg")

        val productPictureData = getProductPictureData()

        val uploadTask = productPicturesRef.putBytes(productPictureData)

        uploadTask.addOnFailureListener { exception ->
            Log.d("SHOOPT_TAG", exception.localizedMessage ?: "Failed to store product picture")
            Toast.makeText(this, exception.localizedMessage, Toast.LENGTH_SHORT).show()
        }.addOnSuccessListener {
            Log.d("SHOOPT_TAG", "Product picture saved!")
            Toast.makeText(this, "Product picture saved!", Toast.LENGTH_SHORT).show()

            productPicturesRef.downloadUrl.addOnSuccessListener {
                val downloadUrl = it.toString()
                Log.d("SHOOPT_TAG", "Download URL: $downloadUrl")

                saveShop(productShop)

                saveProductInFirebaseDatabase(downloadUrl)
            }
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

    private fun saveProductInFirebaseDatabase(productPictureUrl: String) {
        val barcode = if (productBarcodeEditText.text.toString()
                .isEmpty()
        ) "0".toLong() else productBarcodeEditText.text.toString().toLong()
        val timestamp = System.currentTimeMillis()
        val name = productNameEditText.text.toString()
        val price = productPriceEditText.text.toString().toDouble()
        val unitPrice = productUnitPriceEditText.text.toString().toDouble()
        val shop = productShopAutoCompleteTextView.text.toString()

        if (name.isNotEmpty() && !price.isNaN() && !unitPrice.isNaN() && shop.isNotEmpty()) {
            val productsRef = ShooptUtils.getFirebaseDatabaseReference().child("products")

            if (intent.hasExtra("productId")) {
                // Product already exists, update its data
                intent.getStringExtra("productId")?.let {
                    updateProductInRTDB(
                        it,
                        barcode,
                        timestamp,
                        name,
                        price,
                        unitPrice,
                        shop,
                        productPictureUrl,
                        productsRef
                    )
                }
            } else {
                // Product does not exist, create a new one
                addProductToRTDB(
                    barcode,
                    timestamp,
                    name,
                    shop,
                    price,
                    unitPrice,
                    productPictureUrl
                )
            }
        } else {
            displayFailedStorage()
        }
    }

    private fun updateProductInRTDB(
        productId: String,
        barcode: Long,
        timestamp: Long,
        name: String,
        price: Double,
        unitPrice: Double,
        shop: String,
        productPictureUrl: String,
        productsRef: DatabaseReference
    ) {
            if (productId != null) {
                val updatedProduct = Product(
                    productId,
                    barcode,
                    timestamp,
                    name,
                    price,
                    unitPrice,
                    shop,
                    productPictureUrl
                )

                // Update the product in the database
                productsRef.child(productId).setValue(updatedProduct)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("SHOOPT_TAG", "Product updated!")
                            Toast.makeText(
                                this@AddProductActivity,
                                "Product updated!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Log.d("SHOOPT_TAG", "Error updating product: ${task.exception}")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.d("SHOOPT_TAG", e.localizedMessage ?: "Failed to update product")
                        Toast.makeText(
                            this@AddProductActivity,
                            e.localizedMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
        }
    }

    private fun displayFailedStorage() {
        Toast.makeText(
            this@AddProductActivity,
            "Fail to add data because empty field found",
            Toast.LENGTH_SHORT
        )
            .show()
    }

    private fun addProductToRTDB(
        barcode: Long,
        timestamp: Long,
        name: String,
        shop: String,
        price: Double,
        unitPrice: Double,
        pictureUrl: String
    ) {
        val productId = ShooptUtils.getFirebaseDatabaseReference().push().key
        if (productId != null) {
            val product = Product(productId, barcode, timestamp, name, price, unitPrice, shop, pictureUrl)

            ShooptUtils.getFirebaseDatabaseReference().child("products").child(productId)
                .setValue(product)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("SHOOPT_TAG", "Product saved!")
                        Toast.makeText(this, "Product saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("SHOOPT_TAG", "Error: ${task.exception}")
                    }
                }
                .addOnFailureListener { e ->
                    Log.d("SHOOPT_TAG", e.localizedMessage ?: "Failed to store product")
                    Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        }
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

            // Load the product picture from the pictureUrl using your preferred image loading library

            // Example with Glide:
            Glide.with(this)
                .load(pictureUrl)
                .into(productPictureImageButton)
        }
    }

}