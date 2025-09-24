package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest


class RegisterActivity : AppCompatActivity() {
    private var mAuth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_register)

            // Suivi de la vue d'écran pour l'analyse
            AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("register_screen", "RegisterActivity")

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

            val fullNameEditText = findViewById<TextInputEditText>(R.id.editTextName)
            val emailEditText = findViewById<EditText>(R.id.email)
            val passwordEditText = findViewById<EditText>(R.id.password)
            val confirmPasswordEditText = findViewById<TextInputEditText>(R.id.confirm_password)
            val registerButton = findViewById<Button>(R.id.register_button)

            // Récupération du lien de connexion
            val loginLink = findViewById<TextView>(R.id.login_link)

            // Ajout d'un click listener pour rediriger vers l'écran de connexion
            loginLink.setOnClickListener {
                // Suivre le clic sur le lien de connexion
                val params = Bundle().apply {
                    putString("action", "click")
                    putString("category", "navigation")
                    putString("button", "login_link")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", params)

                // Redirection vers LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish() // Fermer l'activité d'inscription
            }

            registerButton.setOnClickListener {
                try {
                    // Suivre le clic sur le bouton d'inscription
                    val clickParams = Bundle().apply {
                        putString("action", "click")
                        putString("category", "registration")
                        putString("button", "register_button")
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", clickParams)

                    val fullName = fullNameEditText.text.toString().trim()
                    val email = emailEditText.text.toString().trim()
                    val password = passwordEditText.text.toString().trim()
                    val confirmPassword = confirmPasswordEditText.text.toString().trim()

                    // Validation des champs
                    if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                        // Suivre les tentatives d'inscription avec champs manquants
                        val valParams = Bundle().apply {
                            putString("action", "validation_error")
                            putString("category", "registration")
                            putString("error_type", "empty_fields")
                            putBoolean("has_name", fullName.isNotEmpty())
                            putBoolean("has_email", email.isNotEmpty())
                            putBoolean("has_password", password.isNotEmpty())
                            putBoolean("has_confirm_password", confirmPassword.isNotEmpty())
                        }
                        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", valParams)

                        Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT)
                            .show()
                        return@setOnClickListener
                    }

                    // Vérifier que les mots de passe correspondent
                    if (password != confirmPassword) {
                        Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // Mesurer le temps de réponse pour l'inscription
                    val startTime = System.currentTimeMillis()

                    mAuth!!.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            // Calculer la durée de l'opération
                            val duration = System.currentTimeMillis() - startTime

                            if (task.isSuccessful) {
                                // Ajouter le nom complet au profil utilisateur
                                val user = mAuth!!.currentUser
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName)
                                    .build()

                                user?.updateProfile(profileUpdates)
                                    ?.addOnCompleteListener { profileTask ->
                                        if (profileTask.isSuccessful) {
                                            // Profil mis à jour avec succès
                                            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","profile_update"); putString("category","registration"); putBoolean("success", true) })
                                        } else {
                                            // Échec de la mise à jour du profil
                                            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","profile_update"); putString("category","registration"); putBoolean("success", false) })

                                            profileTask.exception?.let {
                                                CrashlyticsManager.log("Échec de mise à jour du profil: ${it.message ?: "Message non disponible"}")
                                                CrashlyticsManager.logException(it)
                                            }
                                        }
                                    }

                                // Suivre les inscriptions réussies
                                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("sign_up", Bundle().apply { putString("method","email"); putBoolean("success", true) })

                                // Suivre la performance
                                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("registration_performance", Bundle().apply { putLong("duration_ms", duration) })

                                // Suivre les nouveaux utilisateurs
                                task.result.user?.let { user ->
                                    // Utiliser uniquement l'identifiant anonymisé et non l'email pour confidentialité
                                    AnalyticsService.getInstance(ShooptApplication.instance).setUserId(user.uid)
                                    AnalyticsService.getInstance(ShooptApplication.instance).setUserProperty("user_account_type", "email")
                                    AnalyticsService.getInstance(ShooptApplication.instance).setUserProperty("account_creation_date", System.currentTimeMillis().toString())
                                }

                                Toast.makeText(
                                    this,
                                    getString(R.string.registration_successful),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish() // Return to login screen
                            } else {
                                // Suivre les inscriptions échouées (sans inclure d'informations personnelles)
                                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("sign_up", Bundle().apply { putString("method","email"); putBoolean("success", false) })

                                // Suivre les erreurs typiques (sans données personnelles)
                                task.exception?.let {
                                    val errorMessage = it.message ?: ""
                                    val errorType = when {
                                        errorMessage.contains("email address is already in use") -> "email_already_used"
                                        errorMessage.contains("password is invalid") -> "invalid_password"
                                        errorMessage.contains("badly formatted") -> "invalid_email_format"
                                        else -> "other_auth_error"
                                    }

                                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","registration_error"); putString("category","authentication"); putString("error_type", errorType) })

                                    // Code Crashlytics existant
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
                    // Suivre les erreurs techniques
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","technical_error"); putString("category","registration"); putString("error_type", e.javaClass.simpleName) })

                    // Capture des erreurs lors de l'inscription (code Crashlytics existant)
                    CrashlyticsManager.log("Erreur lors du traitement de l'inscription: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "register_button_click")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Erreur lors de l'inscription: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Suivre les erreurs critiques de l'application
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","critical_error"); putString("category","app_initialization"); putString("activity","RegisterActivity") })

            // Capture des erreurs globales dans onCreate (code Crashlytics existant)
            CrashlyticsManager.log("Erreur globale dans RegisterActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "register_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Une erreur est survenue lors du démarrage de l'application", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Suivre le temps passé sur l'écran d'inscription
        AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("register_screen", "RegisterActivity")
    }
}
