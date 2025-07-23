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
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences
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
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Initialiser et appliquer les préférences utilisateur
            userPreferences = UserPreferences(this)
            userPreferences.applyTheme()

            setContentView(R.layout.activity_main)


            setMainVariables()

            // Enregistrement de l'écran principal dans Analytics
            try {
                AnalyticsManager.logScreenView("Main", "MainActivity")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
            }

            // Check if the user is signed in
            try {
                val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser
                currentUser?.let {
                    val username = it.displayName
                    if (username != null) {
                        val toast = Toast.makeText(this, getString(R.string.welcome_user, username), Toast.LENGTH_LONG)
                        toast.show()
                    }
                }
            } catch (e: Exception) {
                // Capture des erreurs liées à l'accès aux informations utilisateur Firebase
                CrashlyticsManager.log("Erreur lors de l'accès aux informations utilisateur: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "firebase_user_access")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            val logoutButton: ImageButton = findViewById(R.id.logout_button)
            logoutButton.setOnClickListener {
                displayLogoutConfirmation()
            }

            val settingsButton: ImageButton = findViewById(R.id.settings_button)
            settingsButton.setOnClickListener {
                try {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } catch (e: Exception) {
                    // Capture des erreurs liées au lancement de l'activité Settings
                    CrashlyticsManager.log("Erreur lors du lancement de SettingsActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "launch_activity")
                    CrashlyticsManager.setCustomKey("target_activity", "SettingsActivity")
                    CrashlyticsManager.logException(e)

                    // Afficher un message à l'utilisateur
                    Toast.makeText(this, "Impossible d'ouvrir les paramètres. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }

            updateShoppingListImageButton.setOnClickListener {
                try {
                    // Analytics pour le passage à l'écran de mise à jour de la liste
                    AnalyticsManager.logSelectContent("navigation", "button", "update_shopping_list")

                    startActivity(Intent(this, UpdateShoppingListActivity::class.java))
                } catch (e: Exception) {
                    // Capture des erreurs
                    CrashlyticsManager.log("Erreur lors du lancement de UpdateShoppingListActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "launch_activity")
                    CrashlyticsManager.setCustomKey("target_activity", "UpdateShoppingListActivity")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible de mettre à jour la liste. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }

            addOrUpdateProductImageButton.setOnClickListener {
                // Analytics pour le passage à l'écran d'ajout/mise à jour de produit
                AnalyticsManager.logSelectContent("navigation", "button", "add_product")

                displayAddProductWayUserChoice()
            }

            trackShoppingImageButton.setOnClickListener {
                try {
                    // Analytics pour le passage à l'écran de suivi des achats
                    AnalyticsManager.logSelectContent("navigation", "button", "track_shopping")

                    startActivity(Intent(this, TrackShoppingActivity::class.java))
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement de TrackShoppingActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "launch_activity")
                    CrashlyticsManager.setCustomKey("target_activity", "TrackShoppingActivity")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible de suivre les achats. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }

            analyseImageButton.setOnClickListener {
                try {
                    // Analytics pour le passage à l'écran d'analyse
                    AnalyticsManager.logSelectContent("navigation", "button", "analyse")

                    startActivity(Intent(this, AnalyseActivity::class.java))
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement de AnalyseActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "launch_activity")
                    CrashlyticsManager.setCustomKey("target_activity", "AnalyseActivity")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible d'ouvrir l'analyse. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans MainActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "main_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.logException(e)

            // Tentative de récupération pour éviter un crash complet de l'application
            Toast.makeText(this, "Une erreur est survenue. Veuillez redémarrer l'application.", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayAddProductWayUserChoice() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.choose_option))
            builder.setMessage(getString(R.string.scan_barcode_or_add_manually))

            builder.setPositiveButton(getString(R.string.scan_barcode)) { _, _ ->
                // Launch the barcode scanner
                try {
                    barcodeLauncher.launch(ScanOptions())
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement du scanner: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_scanner_launch")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible de lancer le scanner. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton(getString(R.string.add_product_manually)) { _, _ ->
                // Launch the add product manually activity
                try {
                    val addProductIntent = Intent(this, AddProductActivity::class.java)
                    startActivity(addProductIntent)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement de AddProductActivity: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "launch_activity")
                    CrashlyticsManager.setCustomKey("target_activity", "AddProductActivity")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible d'ajouter un produit. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }

            val dialog = builder.create()
            dialog.show()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du choix d'ajout de produit: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "display_product_choice")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.logException(e)
        }
    }

    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        try {
            if (result.contents == null) {
                Toast.makeText(this@MainActivity, getString(R.string.cancelled), Toast.LENGTH_LONG).show()
            } else {
                try {
                    val addProductIntent = Intent(this@MainActivity, AddProductActivity::class.java)
                    addProductIntent.putExtra("barcode", result.contents)
                    startActivity(addProductIntent)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du traitement du code-barres: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_processing")
                    CrashlyticsManager.setCustomKey("barcode", result.contents)
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Impossible de traiter le code-barres. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du traitement du résultat du scan: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "barcode_scan_result")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.logException(e)
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
        try {
            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // Clear user session or perform necessary logout operations
            val logoutIntent = Intent(this, LoginActivity::class.java)
            logoutIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(logoutIntent)
            finish()
        } catch (e: Exception) {
            // Capture des erreurs liées à la déconnexion
            CrashlyticsManager.log("Erreur lors de la déconnexion: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "logout_process")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            // Informer l'utilisateur
            Toast.makeText(this, "Problème lors de la déconnexion. Veuillez réessayer.", Toast.LENGTH_SHORT).show()
        }
    }
}