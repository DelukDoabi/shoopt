package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser


class MainActivity : AppCompatActivity() {
    private lateinit var updateShoppingListImageButton: ImageButton
    private lateinit var addOrUpdateProductImageButton: ImageButton
    private lateinit var trackShoppingImageButton: ImageButton
    private lateinit var analyseImageButton: ImageButton
    private lateinit var addOrUpdateProductTextView: TextView
    private lateinit var trackShoppingTextView: TextView
    private lateinit var analyseTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        setMainVariables()

        // Check if the user is signed in
        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            val username = it.displayName
            if (username != null) {
                val toast = Toast.makeText(this, "Bienvenue, $username!", Toast.LENGTH_LONG)
                toast.show()
            }
        }

        val logoutButton: ImageButton = findViewById(R.id.logout_button)
        logoutButton.setOnClickListener {
            displayLogoutConfirmation()
        }

        updateShoppingListImageButton.setOnClickListener {
            startActivity(Intent(this, UpdateShoppingListActivity::class.java))
        }

        addOrUpdateProductImageButton.setOnClickListener {
            displayAddProductWayUserChoice()
        }

        trackShoppingImageButton.setOnClickListener {
            startActivity(Intent(this, TrackShoppingActivity::class.java))
        }

        analyseImageButton.setOnClickListener {
            startActivity(Intent(this, AnalyseActivity::class.java))
        }
    }

    private fun displayAddProductWayUserChoice() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose an option")
        builder.setMessage("Scan barcode or add product manually")

        builder.setPositiveButton("Scan barcode") { _, _ ->
            // Launch the barcode scanner
            barcodeLauncher.launch(ScanOptions())
        }

        builder.setNegativeButton("Add product manually") { _, _ ->
            // Launch the add product manually activity
            val addProductIntent = Intent(this, AddProductActivity::class.java)
            startActivity(addProductIntent)
        }

        val dialog = builder.create()
        dialog.show()
    }

    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            val addProductIntent = Intent(this@MainActivity, AddProductActivity::class.java)
            addProductIntent.putExtra("barcode", result.contents)
            startActivity(addProductIntent)
        }
    }

    private fun setMainVariables() {
        updateShoppingListImageButton =
            findViewById(R.id.shopping_list_IB)
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

    private fun displayLogoutConfirmation() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to logout?")

        builder.setPositiveButton("Yes") { _, _ ->
            // Perform logout logic
            logoutUser()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun logoutUser() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear user session or perform necessary logout operations
        val logoutIntent = Intent(this, LoginActivity::class.java)
        logoutIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(logoutIntent)
        finish()
    }
}