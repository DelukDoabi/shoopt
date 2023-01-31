package com.dedoware.shoopt.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R

class UpdateShoppingListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_shopping_list)

        supportActionBar?.hide()

    }
}