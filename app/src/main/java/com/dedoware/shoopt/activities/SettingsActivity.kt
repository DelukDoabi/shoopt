package com.dedoware.shoopt.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Masquer l'ActionBar pour afficher notre propre mise en page
        supportActionBar?.hide()

        userPreferences = UserPreferences(this)

        // Initialisation des éléments UI
        initializeUiElements()

        // Chargement des préférences actuelles
        loadCurrentPreferences()

        // Configuration des listeners
        setupListeners()
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
            savePreferences()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        // Bouton de retour
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun savePreferences() {
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
        userPreferences.applyTheme()
    }
}
