package com.dedoware.shoopt

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class AddProductActivity : AppCompatActivity() {
    private lateinit var backImageButton: ImageButton
    private lateinit var productPictureImageButton: ImageButton

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

        backImageButton.setOnClickListener {
            finish()
        }

        productPictureImageButton.setOnClickListener {
            // Open camera
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            resultLauncher.launch(cameraIntent)
        }
    }


    private fun setMainVariables() {
        backImageButton =
            findViewById(R.id.back_IB)
        productPictureImageButton =
            findViewById(R.id.product_picture_IB)
    }
}