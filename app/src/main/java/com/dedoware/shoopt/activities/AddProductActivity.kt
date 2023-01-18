package com.dedoware.shoopt.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.dedoware.shoopt.R
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.utils.ShooptUtils
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File


class AddProductActivity : AppCompatActivity() {
    private lateinit var backImageButton: ImageButton
    private lateinit var productPictureImageButton: ImageButton

    private lateinit var saveProductImageButton: ImageButton
    private lateinit var productBarcodeEditText: EditText
    private lateinit var productNameEditText: EditText
    private lateinit var productPriceEditText: EditText
    private lateinit var productUnitPriceEditText: EditText
    private lateinit var productShopEditText: EditText

    private lateinit var productPictureFile: File


    // Get your image
    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val (productPictureOrientation, productPictureBitmap) = getProductPictureBitmap()

                productPictureImageButton.setImageBitmap(
                    rotateProductPictureBitmap(
                        productPictureOrientation,
                        productPictureBitmap
                    )
                )
                
                productPictureBitmap.recycle()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        supportActionBar?.hide()

        // TODO NBE if not working put findViewById methods after setContentView
        setMainVariables()

        ShooptUtils.doAfterInitFirebase(baseContext, null)

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

        // Save product information to Firebase Realtime Database
        saveProductImageButton.setOnClickListener {
            saveProductPictureInFirebaseStorage()
        }
    }

    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val imageUri =
            FileProvider.getUriForFile(this, "com.dedoware.shoopt.fileprovider", productPictureFile)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

        resultLauncher.launch(cameraIntent)
    }

    private fun saveProductPictureInFirebaseStorage() {
        val productPictureUri =
            FileProvider.getUriForFile(this, "com.dedoware.shoopt.fileprovider", productPictureFile)

        val storageRef = Firebase.storage.reference

        val productBarcode = productBarcodeEditText.text.toString().toLong()
        val productName = productNameEditText.text.toString()
        val productShop = productShopEditText.text.toString()


        val productPicturesRef =
            storageRef.child("product-pictures/$productBarcode-$productName-$productShop.jpg")

        val uploadTask = productPicturesRef.putFile(productPictureUri)
        uploadTask.addOnFailureListener { exception ->
            Log.d("SHOOPT_TAG", exception.localizedMessage ?: "Failed to store product picture")
            Toast.makeText(this, exception.localizedMessage, Toast.LENGTH_SHORT).show()
        }.addOnSuccessListener {
            Log.d("SHOOPT_TAG", "Product picture saved!")
            Toast.makeText(this, "Product picture saved!", Toast.LENGTH_SHORT).show()

            productPicturesRef.downloadUrl.addOnSuccessListener {
                val downloadUrl = it.toString()
                Log.d("SHOOPT_TAG", "Download URL: $downloadUrl")

                saveProductInFirebaseDatabase(downloadUrl)
            }
        }
    }

    private fun getScannedBarcode() {
        val scannedBarcode = intent.getStringExtra("barcode")
        if (scannedBarcode != null) productBarcodeEditText.setText(scannedBarcode)
    }

    private fun saveProductInFirebaseDatabase(productPictureUrl: String) {
        val barcode = if (productBarcodeEditText.text.toString()
                .isEmpty()
        ) "0".toLong() else productBarcodeEditText.text.toString().toLong()
        val name = productNameEditText.text.toString()
        val price = productPriceEditText.text.toString().toDouble()
        val unitPrice = productUnitPriceEditText.text.toString().toDouble()
        val shop = productShopEditText.text.toString()
        val id = "$barcode-$name-$shop"

        if (name.isNotEmpty() && !price.isNaN() && !unitPrice.isNaN() && shop.isNotEmpty()) {
            addProductToRTDB(id, barcode, name, shop, price, unitPrice, productPictureUrl)
        } else {
            displayFailedStorage()
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
        id: String,
        barcode: Long,
        name: String,
        shop: String,
        price: Double,
        unitPrice: Double,
        pictureUrl: String
    ) {
        val productId = ShooptUtils.getFirebaseDatabaseReference().push().key
        if (productId != null) {
            val product = Product(id, barcode, name, price, unitPrice, shop, pictureUrl)

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

    private fun getProductPictureBitmap(): Pair<Int, Bitmap> {
        val exif = ExifInterface(productPictureFile.path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val options = BitmapFactory.Options()
        options.inSampleSize = 10

        val bitmap = BitmapFactory.decodeFile(productPictureFile.path, options)
        return Pair(orientation, bitmap)
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

    private fun setMainVariables() {
        backImageButton = findViewById(R.id.back_IB)
        productPictureImageButton = findViewById(R.id.product_picture_IB)
        saveProductImageButton = findViewById(R.id.save_product_IB)
        productBarcodeEditText = findViewById(R.id.product_barcode_ET)
        productNameEditText = findViewById(R.id.product_name_ET)
        productPriceEditText = findViewById(R.id.product_price_ET)
        productUnitPriceEditText = findViewById(R.id.product_unit_price_ET)
        productShopEditText = findViewById(R.id.product_shop_ET)
    }
}