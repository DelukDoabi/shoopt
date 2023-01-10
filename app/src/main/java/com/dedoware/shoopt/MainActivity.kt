package com.dedoware.shoopt

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


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

        addOrUpdateProductImageButton.setOnClickListener {}
    }

    private fun setMainVariables() {
        addOrUpdateProductImageButton =
            findViewById(R.id.add_or_update_product_IB)
        trackShoppingImageButton =
            findViewById(R.id.track_shopping_IB)
        analyseImageButton =
            findViewById(R.id.analyse_IB)

        addOrUpdateProductTextView =
            findViewById(R.id.add_or_update_product_TV)
        trackShoppingTextView =
            findViewById(R.id.track_shopping_TV)
        analyseTextView =
            findViewById(R.id.analyse_TV)
    }

}