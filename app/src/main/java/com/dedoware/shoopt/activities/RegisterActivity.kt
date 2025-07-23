package com.dedoware.shoopt.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.firebase.auth.FirebaseAuth


class RegisterActivity : AppCompatActivity() {
    private var mAuth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_register)

            try {
                mAuth = FirebaseAuth.getInstance()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de FirebaseAuth: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "firebase_auth_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'authentification", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val emailEditText = findViewById<EditText>(R.id.email)
            val passwordEditText = findViewById<EditText>(R.id.password)
            val registerButton = findViewById<Button>(R.id.register_button)

            registerButton.setOnClickListener {
                try {
                    val email = emailEditText.text.toString().trim { it <= ' ' }
                    val password = passwordEditText.text.toString().trim { it <= ' ' }

                    if (email.isEmpty() || password.isEmpty()) {
                        Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT)
                            .show()
                        return@setOnClickListener
                    }

                    mAuth!!.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    getString(R.string.registration_successful),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish() // Return to login screen
                            } else {
                                // Log de l'erreur dans Crashlytics
                                task.exception?.let {
                                    CrashlyticsManager.log("Échec de l'inscription: ${it.message ?: "Message non disponible"}")
                                    CrashlyticsManager.setCustomKey("error_location", "user_registration")
                                    CrashlyticsManager.setCustomKey("exception_class", it.javaClass.name)
                                    CrashlyticsManager.setCustomKey("exception_message", it.message ?: "Message non disponible")
                                    CrashlyticsManager.setCustomKey("email", email) // Note: ne stocker que des informations non sensibles
                                    CrashlyticsManager.logException(it)
                                }

                                Toast.makeText(
                                    this,
                                    getString(R.string.registration_failed, task.exception!!.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } catch (e: Exception) {
                    // Capture des erreurs lors de l'inscription
                    CrashlyticsManager.log("Erreur lors du traitement de l'inscription: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "register_button_click")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Erreur lors de l'inscription: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans RegisterActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "register_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Une erreur est survenue lors du démarrage de l'application", Toast.LENGTH_LONG).show()
        }
    }
}
