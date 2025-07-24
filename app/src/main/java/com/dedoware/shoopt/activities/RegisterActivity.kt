package com.dedoware.shoopt.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.AnalyticsManager
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
            AnalyticsManager.logScreenView("register_screen", "RegisterActivity")

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

            registerButton.setOnClickListener {
                try {
                    // Suivre le clic sur le bouton d'inscription
                    AnalyticsManager.logUserAction(
                        action = "click",
                        category = "registration",
                        additionalParams = mapOf("button" to "register_button")
                    )

                    val fullName = fullNameEditText.text.toString().trim()
                    val email = emailEditText.text.toString().trim()
                    val password = passwordEditText.text.toString().trim()
                    val confirmPassword = confirmPasswordEditText.text.toString().trim()

                    // Validation des champs
                    if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                        // Suivre les tentatives d'inscription avec champs manquants
                        AnalyticsManager.logUserAction(
                            action = "validation_error",
                            category = "registration",
                            additionalParams = mapOf(
                                "error_type" to "empty_fields",
                                "has_name" to (fullName.isNotEmpty()),
                                "has_email" to (email.isNotEmpty()),
                                "has_password" to (password.isNotEmpty()),
                                "has_confirm_password" to (confirmPassword.isNotEmpty())
                            )
                        )

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
                                            AnalyticsManager.logUserAction(
                                                action = "profile_update",
                                                category = "registration",
                                                additionalParams = mapOf("success" to true)
                                            )
                                        } else {
                                            // Échec de la mise à jour du profil
                                            AnalyticsManager.logUserAction(
                                                action = "profile_update",
                                                category = "registration",
                                                additionalParams = mapOf("success" to false)
                                            )

                                            profileTask.exception?.let {
                                                CrashlyticsManager.log("Échec de mise à jour du profil: ${it.message ?: "Message non disponible"}")
                                                CrashlyticsManager.logException(it)
                                            }
                                        }
                                    }

                                // Suivre les inscriptions réussies
                                AnalyticsManager.logAuthEvent(
                                    method = "email",
                                    success = true
                                )

                                // Suivre la performance
                                AnalyticsManager.logPerformanceEvent(
                                    "registration_performance",
                                    duration
                                )

                                // Suivre les nouveaux utilisateurs
                                task.result.user?.let { user ->
                                    // Utiliser uniquement l'identifiant anonymisé et non l'email pour confidentialité
                                    AnalyticsManager.setUserId(user.uid)
                                    AnalyticsManager.setUserProperty("user_account_type", "email")
                                    AnalyticsManager.setUserProperty("account_creation_date", System.currentTimeMillis().toString())
                                }

                                Toast.makeText(
                                    this,
                                    getString(R.string.registration_successful),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish() // Return to login screen
                            } else {
                                // Suivre les inscriptions échouées (sans inclure d'informations personnelles)
                                AnalyticsManager.logAuthEvent(
                                    method = "email",
                                    success = false
                                )

                                // Suivre les erreurs typiques (sans données personnelles)
                                task.exception?.let {
                                    val errorMessage = it.message ?: ""
                                    val errorType = when {
                                        errorMessage.contains("email address is already in use") -> "email_already_used"
                                        errorMessage.contains("password is invalid") -> "invalid_password"
                                        errorMessage.contains("badly formatted") -> "invalid_email_format"
                                        else -> "other_auth_error"
                                    }

                                    AnalyticsManager.logUserAction(
                                        action = "registration_error",
                                        category = "authentication",
                                        additionalParams = mapOf("error_type" to errorType)
                                    )

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
                    AnalyticsManager.logUserAction(
                        action = "technical_error",
                        category = "registration",
                        additionalParams = mapOf("error_type" to e.javaClass.simpleName)
                    )

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
            AnalyticsManager.logUserAction(
                action = "critical_error",
                category = "app_initialization",
                additionalParams = mapOf("activity" to "RegisterActivity")
            )

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
        AnalyticsManager.logScreenView("register_screen", "RegisterActivity")
    }
}
