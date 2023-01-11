package com.dedoware.shoopt

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class AddProductActivity : AppCompatActivity() {
    private lateinit var backImageButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        // TODO NBE if not working put findViewById methods after setContentView
        setMainVariables()

        backImageButton.setOnClickListener {
            finish()
        }
    }


    private fun setMainVariables() {
        backImageButton =
            findViewById(R.id.back_IB)
    }
}