package com.dedoware.shoopt.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var userPreferences: UserPreferences

    // UI Elements
    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var themeLightRadio: RadioButton
    private lateinit var themeDarkRadio: RadioButton
    private lateinit var themeSystemRadio: RadioButton
    private lateinit var currencySpinner: Spinner
    private lateinit var saveButton: ImageButton
    private lateinit var backButton: ImageButton

    // Liste des devises disponibles
    private val currencies = arrayOf("EUR (€)", "USD ($)", "GBP (£)", "JPY (¥)", "CAD (C$)", "CHF (Fr)", "AUD (A$)")
    private val currencyCodes = arrayOf("EUR", "USD", "GBP", "JPY", "CAD", "CHF", "AUD")

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            // Masquer l'ActionBar pour afficher notre propre mise en page
            supportActionBar?.hide()

            try {
                userPreferences = UserPreferences(this)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation des préférences utilisateur: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_user_preferences_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation des préférences", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            try {
                // Initialisation des éléments UI
                initializeUiElements()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation des éléments UI: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_ui_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'interface", Toast.LENGTH_SHORT).show()
            }

            try {
                // Chargement des préférences actuelles
                loadCurrentPreferences()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du chargement des préférences: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_load_preferences")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors du chargement des préférences", Toast.LENGTH_SHORT).show()
            }

            try {
                // Configuration des listeners
                setupListeners()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la configuration des listeners: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_setup_listeners")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de la configuration des contrôles", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans SettingsActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "settings_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Une erreur est survenue lors du démarrage des paramètres", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeUiElements() {
        themeRadioGroup = findViewById(R.id.theme_radio_group)
        themeLightRadio = findViewById(R.id.theme_light)
        themeDarkRadio = findViewById(R.id.theme_dark)
        themeSystemRadio = findViewById(R.id.theme_system)
        currencySpinner = findViewById(R.id.currency_spinner)
        saveButton = findViewById(R.id.save_settings_button)
        backButton = findViewById(R.id.back_IB)

        // Configuration du spinner de devises
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        currencySpinner.adapter = adapter
    }

    private fun loadCurrentPreferences() {
        // Sélection du thème actuel
        when (userPreferences.themeMode) {
            UserPreferences.THEME_LIGHT -> themeLightRadio.isChecked = true
            UserPreferences.THEME_DARK -> themeDarkRadio.isChecked = true
            else -> themeSystemRadio.isChecked = true
        }

        // Sélection de la devise actuelle
        val currencyIndex = currencyCodes.indexOf(userPreferences.currency)
        if (currencyIndex != -1) {
            currencySpinner.setSelection(currencyIndex)
        }
    }

    private fun setupListeners() {
        // Bouton de sauvegarde
        saveButton.setOnClickListener {
            try {
                savePreferences()
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la sauvegarde des préférences: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_save_button_click")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de la sauvegarde des paramètres", Toast.LENGTH_SHORT).show()
            }
        }

        // Bouton de retour
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun savePreferences() {
        try {
            // Sauvegarde du thème
            val themeMode = when (themeRadioGroup.checkedRadioButtonId) {
                R.id.theme_light -> UserPreferences.THEME_LIGHT
                R.id.theme_dark -> UserPreferences.THEME_DARK
                else -> UserPreferences.THEME_SYSTEM
            }
            userPreferences.themeMode = themeMode

            // Sauvegarde de la devise
            val currencyIndex = currencySpinner.selectedItemPosition
            if (currencyIndex != -1 && currencyIndex < currencyCodes.size) {
                userPreferences.currency = currencyCodes[currencyIndex]
            }

            // Application immédiate du thème
            try {
                userPreferences.applyTheme()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'application du thème: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_apply_theme")
                CrashlyticsManager.setCustomKey("theme_mode", themeMode)
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                // On ne lève pas d'erreur pour permettre de continuer malgré l'échec de l'application du thème
                Toast.makeText(this, "Les préférences ont été sauvegardées mais le thème n'a pas pu être appliqué", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la sauvegarde des préférences: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "settings_save_preferences")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            throw e // On relance l'exception pour être capturée dans le listener
        }
    }
}
