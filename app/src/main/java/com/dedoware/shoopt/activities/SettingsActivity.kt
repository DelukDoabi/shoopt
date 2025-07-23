package com.dedoware.shoopt.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.AnalyticsManager
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

            // Suivi de la vue d'écran pour l'analyse
            AnalyticsManager.logScreenView("settings_screen", "SettingsActivity")

            // Masquer l'ActionBar pour afficher notre propre mise en page
            supportActionBar?.hide()

            try {
                userPreferences = UserPreferences(this)
            } catch (e: Exception) {
                // Analytics: suivre l'erreur d'initialisation des préférences
                AnalyticsManager.logUserAction(
                    action = "initialization_error",
                    category = "settings",
                    additionalParams = mapOf("component" to "user_preferences")
                )

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
                // Analytics: suivre l'erreur d'initialisation UI
                AnalyticsManager.logUserAction(
                    action = "initialization_error",
                    category = "settings",
                    additionalParams = mapOf("component" to "ui_elements")
                )

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
                // Analytics: suivre l'erreur de chargement des préférences
                AnalyticsManager.logUserAction(
                    action = "initialization_error",
                    category = "settings",
                    additionalParams = mapOf("component" to "load_preferences")
                )

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
                // Analytics: suivre l'erreur de configuration des listeners
                AnalyticsManager.logUserAction(
                    action = "initialization_error",
                    category = "settings",
                    additionalParams = mapOf("component" to "setup_listeners")
                )

                CrashlyticsManager.log("Erreur lors de la configuration des listeners: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_setup_listeners")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de la configuration des contrôles", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Analytics: suivre les erreurs critiques
            AnalyticsManager.logUserAction(
                action = "critical_error",
                category = "app_initialization",
                additionalParams = mapOf("activity" to "SettingsActivity")
            )

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

        // Analytics: suivre les options de devise disponibles
        AnalyticsManager.logUserAction(
            action = "available_options",
            category = "settings",
            additionalParams = mapOf(
                "currency_count" to currencies.size,
                "theme_options" to 3 // light, dark, system
            )
        )
    }

    private fun loadCurrentPreferences() {
        // Sélection du thème actuel
        val themeMode = when (userPreferences.themeMode) {
            UserPreferences.THEME_LIGHT -> "light"
            UserPreferences.THEME_DARK -> "dark"
            else -> "system"
        }

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

        // Analytics: suivre les préférences actuelles chargées
        AnalyticsManager.logUserAction(
            action = "current_preferences",
            category = "settings",
            additionalParams = mapOf(
                "theme_mode" to themeMode,
                "currency" to (userPreferences.currency ?: "default")
            )
        )
    }

    private fun setupListeners() {
        // Bouton de sauvegarde
        saveButton.setOnClickListener {
            try {
                // Analytics: suivre le clic sur le bouton de sauvegarde
                AnalyticsManager.logUserAction(
                    action = "click",
                    category = "settings",
                    additionalParams = mapOf("button" to "save_settings")
                )

                val startTime = System.currentTimeMillis()
                savePreferences()
                val duration = System.currentTimeMillis() - startTime

                // Analytics: suivre la performance de la sauvegarde
                AnalyticsManager.logPerformanceEvent("settings_save_performance", duration)

                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                // Analytics: suivre l'échec de sauvegarde
                AnalyticsManager.logUserAction(
                    action = "save_error",
                    category = "settings",
                    additionalParams = mapOf("error_type" to e.javaClass.simpleName)
                )

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
            // Analytics: suivre le clic sur le bouton de retour
            AnalyticsManager.logUserAction(
                action = "click",
                category = "settings",
                additionalParams = mapOf("button" to "back")
            )

            finish()
        }

        // Suivi des changements de thème
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.theme_light -> "light"
                R.id.theme_dark -> "dark"
                else -> "system"
            }

            // Analytics: suivre le changement de thème
            AnalyticsManager.logUserAction(
                action = "change_preference",
                category = "settings",
                additionalParams = mapOf(
                    "preference_type" to "theme",
                    "selected_value" to selectedTheme
                )
            )
        }

        // Suivi des changements de devise
        currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < currencyCodes.size) {
                    val selectedCurrency = currencyCodes[position]

                    // Analytics: suivre le changement de devise
                    AnalyticsManager.logUserAction(
                        action = "change_preference",
                        category = "settings",
                        additionalParams = mapOf(
                            "preference_type" to "currency",
                            "selected_value" to selectedCurrency
                        )
                    )
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Ne rien faire
            }
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
            val themeModeString = when (themeMode) {
                UserPreferences.THEME_LIGHT -> "light"
                UserPreferences.THEME_DARK -> "dark"
                else -> "system"
            }
            userPreferences.themeMode = themeMode

            // Sauvegarde de la devise
            val currencyIndex = currencySpinner.selectedItemPosition
            var selectedCurrency = "default"
            if (currencyIndex != -1 && currencyIndex < currencyCodes.size) {
                selectedCurrency = currencyCodes[currencyIndex]
                userPreferences.currency = selectedCurrency
            }

            // Analytics: suivre les préférences sauvegardées
            AnalyticsManager.logUserAction(
                action = "save_preferences",
                category = "settings",
                additionalParams = mapOf(
                    "theme_mode" to themeModeString,
                    "currency" to selectedCurrency
                )
            )

            // Application immédiate du thème
            try {
                userPreferences.applyTheme()
            } catch (e: Exception) {
                // Analytics: suivre l'échec d'application du thème
                AnalyticsManager.logUserAction(
                    action = "theme_application_error",
                    category = "settings",
                    additionalParams = mapOf(
                        "theme_mode" to themeModeString,
                        "error_type" to e.javaClass.simpleName
                    )
                )

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

    override fun onResume() {
        super.onResume()
        // Suivre le temps passé sur l'écran des paramètres
        AnalyticsManager.logScreenView("settings_screen", "SettingsActivity")
    }
}
