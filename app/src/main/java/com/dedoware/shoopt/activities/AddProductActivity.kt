package com.dedoware.shoopt.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.model.Product
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class AddProductActivity : AppCompatActivity() {
    private lateinit var backImageButton: ImageButton
    private lateinit var productPictureImageButton: ImageButton

    private lateinit var saveProductImageButton: ImageButton
    private lateinit var productBarcodeEditText: EditText
    private lateinit var productNameEditText: EditText
    private lateinit var productPriceEditText: EditText
    private lateinit var productUnitPriceEditText: EditText
    private lateinit var productShopEditText: EditText

    // Firebase Realtime Database
    private lateinit var database: DatabaseReference

    // Get your image
    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (result?.data != null) {
                    productPictureImageButton.setImageBitmap(result.data?.extras?.get("data") as Bitmap)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        // TODO NBE if not working put findViewById methods after setContentView
        setMainVariables()

        // Initialize Firebase Realtime Database
        database =
            Firebase.database("https://shoopt-9ab47-default-rtdb.europe-west1.firebasedatabase.app/")
                .reference

        backImageButton.setOnClickListener {
            finish()
        }

        productPictureImageButton.setOnClickListener {
            // Open camera
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            resultLauncher.launch(cameraIntent)
        }

        // Save product information to Firebase Realtime Database
        saveProductImageButton.setOnClickListener {
            val barcode = productBarcodeEditText.text.toString().toInt()
            val name = productNameEditText.text.toString()
            val price = productPriceEditText.text.toString().toDouble()
            val unitPrice = productUnitPriceEditText.text.toString().toDouble()
            val shop = productShopEditText.text.toString()

            // Check if all fields are filled
            if (barcode != null && name.isNotEmpty() && !price.isNaN() && !unitPrice.isNaN() && shop.isNotEmpty()) {
                val productId = database.push().key
                if (productId != null) {
                    val product = Product(barcode, name, price, unitPrice, shop)

                    // Save product information to Firebase Realtime Database
                    database.child("products").child(productId).setValue(product)
                        .addOnFailureListener { e ->
                            Log.d("LOG_TAG", e.localizedMessage)
                        }
                    // do something here to indicate that the product was saved successfully
                }
            } else {
                // do something here to indicate that some fields are empty
            }
        }
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