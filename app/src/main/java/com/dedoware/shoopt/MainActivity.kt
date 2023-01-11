package com.dedoware.shoopt

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator


class MainActivity : AppCompatActivity() {

    // TODO NBE if not working put findViewById methods after setContentView
    private lateinit var addOrUpdateProductImageButton: ImageButton
    private lateinit var trackShoppingImageButton: ImageButton
    private lateinit var analyseImageButton: ImageButton
    private lateinit var addOrUpdateProductTextView: TextView
    private lateinit var trackShoppingTextView: TextView
    private lateinit var analyseTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO NBE if not working put findViewById methods after setContentView
        setMainVariables()

        addOrUpdateProductImageButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Choose an option")
            builder.setMessage("Scan barcode or add product manually")

            builder.setPositiveButton("Scan barcode") { _, _ ->
                // Launch the barcode scanner
                IntentIntegrator(this).initiateScan()
            }

            builder.setNegativeButton("Add product manually") { _, _ ->
                // Launch the add product manually activity
                val addProductIntent = Intent(this, AddProductActivity::class.java)
                startActivity(addProductIntent)
            }

            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun setMainVariables() {
        addOrUpdateProductImageButton =
            findViewById(R.id.add_or_update_product_IB)
        trackShoppingImageButton =
            findViewById(R.id.track_shopping_IB)
        analyseImageButton =
            findViewById(R.id.analyse_IB)

        addOrUpdateProductTextView =
            findViewById(R.id.save_product_TV)
        trackShoppingTextView =
            findViewById(R.id.track_shopping_TV)
        analyseTextView =
            findViewById(R.id.analyse_TV)
    }

}