package com.dedoware.shoopt

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    // TODO NBE if not working put findViewById methods after setContentView
    private val addOrUpdateProductImageButton: ImageButton =
        findViewById(R.id.add_or_update_product_IB)
    private val trackShoppingImageButton: ImageButton =
        findViewById(R.id.track_shopping_IB)
    private val analyseImageButton: ImageButton =
        findViewById(R.id.analyse_IB)

    private val addOrUpdateProductTextView: TextView =
        findViewById(R.id.add_or_update_product_TV)
    private val trackShoppingTextView: TextView =
        findViewById(R.id.track_shopping_TV)
    private val analyseTextView: TextView =
        findViewById(R.id.analyse_TV)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addOrUpdateProductImageButton.setOnClickListener {}
    }

}