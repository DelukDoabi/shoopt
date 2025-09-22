package com.dedoware.shoopt.activities

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.gamification.ui.UserProfileFragment
import com.dedoware.shoopt.utils.UserPreferences

/**
 * Activité pour afficher le profil utilisateur avec le système de gamification
 */
class UserProfileActivity : AppCompatActivity() {

    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser et appliquer les préférences utilisateur
        userPreferences = UserPreferences.getInstance(this)
        userPreferences.applyTheme()

        setContentView(R.layout.activity_user_profile)

        // Configuration du bouton retour
        val backButton: ImageButton = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }

        // Charger le fragment de profil utilisateur
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UserProfileFragment())
                .commit()
        }
    }
}
