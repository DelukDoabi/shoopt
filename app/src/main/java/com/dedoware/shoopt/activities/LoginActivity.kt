package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider


class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        val emailEditText = findViewById<EditText>(R.id.editTextEmail)
        val passwordEditText = findViewById<EditText>(R.id.editTextPassword)
        val emailLoginButton = findViewById<Button>(R.id.email_sign_in_button)

        emailLoginButton.setOnClickListener { v ->
            val email = emailEditText.text.toString().trim { it <= ' ' }
            val password = passwordEditText.text.toString().trim { it <= ' ' }

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful()) {
                        // Pas de logging pour les cas de succès
                        Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT)
                            .show()
                        startActivity(
                            Intent(
                                this,
                                MainActivity::class.java
                            )
                        ) // Navigate to the main app
                        finish()
                    } else {
                        // Log de l'erreur dans Crashlytics
                        task.exception?.let {
                            CrashlyticsManager.log("Échec de connexion par email: ${it.message}")
                            CrashlyticsManager.logException(it)
                            CrashlyticsManager.setCustomKey("auth_method", "email")
                            CrashlyticsManager.setCustomKey("email", email) // Attention: ne stockez que des infos non sensibles
                        }

                        Toast.makeText(
                            this,
                            getString(R.string.login_failed, task.exception!!.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // Google Sign-In
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)

        findViewById<Button>(R.id.google_sign_in_button).setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // Add the registration link logic
        findViewById<TextView>(R.id.register_link).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.forgot_password_link).setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Association de l'ID utilisateur pour les futurs rapports de crash
                        // On garde uniquement l'association de l'utilisateur pour les rapports futurs
                        auth.currentUser?.uid?.let { userId ->
                            CrashlyticsManager.setUserId(userId)
                        }

                        Toast.makeText(this, getString(R.string.google_sign_in_successful), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        // Log de l'erreur dans Crashlytics
                        task.exception?.let {
                            CrashlyticsManager.log("Échec de l'authentification Firebase avec Google: ${it.message}")
                            CrashlyticsManager.logException(it)

                            // Ajout d'informations contextuelles sur le type d'erreur
                            CrashlyticsManager.setCustomKey("auth_method", "google")
                            CrashlyticsManager.setCustomKey("error_phase", "firebase_auth")

                            // Capture de la stack trace complète
                            it.printStackTrace()
                        }

                        Toast.makeText(this, getString(R.string.authentication_failed, task.exception?.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                // Capture détaillée de l'erreur Google Sign-In avec Crashlytics
                CrashlyticsManager.log("Google Sign-In ApiException: Code ${e.statusCode}, Message: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("google_sign_in_error_code", e.statusCode)
                CrashlyticsManager.setCustomKey("auth_method", "google")
                CrashlyticsManager.setCustomKey("error_phase", "google_signin")
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.setCustomKey("exception_cause", e.cause?.toString() ?: "Cause inconnue")
                CrashlyticsManager.logException(e)  // Ceci capture la stack trace complète

                // Affichage plus détaillé de l'erreur Google Sign-In
                Toast.makeText(this, getString(R.string.google_sign_in_failed, "Code: ${e.statusCode}, Message: ${e.message ?: ""}"), Toast.LENGTH_LONG).show()
                // Log plus détaillé pour le débogage
                Log.e("GoogleSignIn", "Google sign in failed with code: ${e.statusCode}", e)
            } catch (e: Exception) {
                // Capture des erreurs inattendues avec Crashlytics
                CrashlyticsManager.log("Erreur inattendue lors de la connexion Google: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("auth_method", "google")
                CrashlyticsManager.setCustomKey("error_phase", "unknown")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.setCustomKey("exception_cause", e.cause?.toString() ?: "Cause inconnue")
                CrashlyticsManager.logException(e)  // Ceci capture la stack trace complète

                Toast.makeText(this, getString(R.string.unexpected_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                Log.e("GoogleSignIn", "Unexpected error during Google sign in", e)
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        val emailInput = EditText(this).apply {
            hint = getString(R.string.enter_your_email)
        }
        builder.setTitle(getString(R.string.reset_password))
            .setMessage(getString(R.string.enter_email_to_reset))
            .setView(emailInput)
            .setPositiveButton(getString(R.string.send)) { _, _ ->
                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendPasswordResetEmail(email)
                } else {
                    Toast.makeText(this, getString(R.string.email_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancelled), null)
            .create()
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, getString(R.string.password_reset_sent), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.error, task.exception?.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val RC_SIGN_IN = 9001
    }
}