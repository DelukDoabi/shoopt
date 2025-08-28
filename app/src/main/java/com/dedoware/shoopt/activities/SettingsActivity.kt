package com.dedoware.shoopt.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import java.io.InputStream

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
    private lateinit var replayOnboardingCard: CardView
    private lateinit var replayAddProductGuideCard: CardView

    private lateinit var currencyList: List<Currency>

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            // Suivi de la vue d'écran pour l'analyse
            AnalyticsManager.logScreenView("settings_screen", "SettingsActivity")

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
        try {
            themeRadioGroup = findViewById(R.id.theme_radio_group) ?: throw IllegalStateException("Missing theme_radio_group")
            themeLightRadio = findViewById(R.id.theme_light) ?: throw IllegalStateException("Missing theme_light")
            themeDarkRadio = findViewById(R.id.theme_dark) ?: throw IllegalStateException("Missing theme_dark")
            themeSystemRadio = findViewById(R.id.theme_system) ?: throw IllegalStateException("Missing theme_system")
            currencySpinner = findViewById(R.id.currency_spinner) ?: throw IllegalStateException("Missing currency_spinner")
            saveButton = findViewById(R.id.save_settings_button) ?: throw IllegalStateException("Missing save_settings_button")
            backButton = findViewById(R.id.back_IB) ?: throw IllegalStateException("Missing back_IB")
            replayOnboardingCard = findViewById(R.id.replay_onboarding_card) ?: throw IllegalStateException("Missing replay_onboarding_card")
            replayAddProductGuideCard = findViewById(R.id.replay_add_product_guide_card)

            // Configuration du spinner de devises
            currencyList = loadCurrenciesFromJson()
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencyList.map { it.name })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            currencySpinner.adapter = adapter

            // Analytics: suivre les options de devise disponibles
            AnalyticsManager.logUserAction(
                action = "available_options",
                category = "settings",
                additionalParams = mapOf(
                    "currency_count" to currencyList.size,
                    "theme_options" to 3 // light, dark, system
                )
            )
        } catch (e: Exception) {
            // Log and handle initialization errors
            AnalyticsManager.logUserAction(
                action = "ui_initialization_error",
                category = "settings",
                additionalParams = mapOf("error" to (e.message ?: "Unknown error"))
            )
            CrashlyticsManager.log("UI initialization error: ${e.message ?: "Unknown error"}")
            CrashlyticsManager.logException(e)
            Toast.makeText(this, "Erreur lors de l'initialisation de l'interface", Toast.LENGTH_SHORT).show()
            throw e
        }
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
        val selectedCurrencyCode = if (userPreferences.currency.isNullOrEmpty()) {
            // Détection intelligente de la devise par défaut selon le pays
            val country = java.util.Locale.getDefault().country
            val defaultCurrencyByCountry = mapOf(
                "FR" to "EUR",
                "BE" to "EUR",
                "DE" to "EUR",
                "IT" to "EUR",
                "ES" to "EUR",
                "PT" to "EUR",
                "NL" to "EUR",
                "LU" to "EUR",
                "IE" to "EUR",
                "FI" to "EUR",
                "AT" to "EUR",
                "GR" to "EUR",
                "US" to "USD",
                "GB" to "GBP",
                "CA" to "CAD",
                "CH" to "CHF",
                "JP" to "JPY",
                "CN" to "CNY",
                "IN" to "INR",
                "BR" to "BRL",
                "RU" to "RUB",
                "AU" to "AUD",
                "MX" to "MXN",
                "SE" to "SEK",
                "NO" to "NOK",
                "DK" to "DKK",
                "PL" to "PLN",
                // Pays arabes principaux
                "TN" to "TND", // Tunisie
                "MA" to "MAD", // Maroc
                "DZ" to "DZD", // Algérie
                "EG" to "EGP", // Égypte
                "SA" to "SAR", // Arabie Saoudite
                "AE" to "AED", // Émirats Arabes Unis
                "QA" to "QAR", // Qatar
                "KW" to "KWD", // Koweït
                "OM" to "OMR", // Oman
                "BH" to "BHD", // Bahreïn
                "JO" to "JOD", // Jordanie
                "LB" to "LBP", // Liban
                "IQ" to "IQD", // Irak
                "SD" to "SDG", // Soudan
                "YE" to "YER", // Yémen
                "LY" to "LYD", // Libye
                "SY" to "SYP", // Syrie
            )
            defaultCurrencyByCountry[country] ?: "USD"
        } else userPreferences.currency
        val currencyIndex = currencyList.indexOfFirst { it.code == selectedCurrencyCode }.let { if (it == -1) 0 else it }
        currencySpinner.setSelection(currencyIndex)

        // Analytics: suivre les préférences actuelles chargées
        AnalyticsManager.logUserAction(
            action = "current_preferences",
            category = "settings",
            additionalParams = mapOf(
                "theme_mode" to themeMode,
                "currency" to userPreferences.currency
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

        // Bouton de replay de l'onboarding
        replayOnboardingCard.setOnClickListener {
            try {
                // Analytics: suivre le clic sur le bouton de replay onboarding
                AnalyticsManager.logUserAction(
                    action = "click",
                    category = "settings",
                    additionalParams = mapOf("button" to "replay_onboarding")
                )

                // Réinitialiser le flag d'onboarding complété
                UserPreferences.setOnboardingCompleted(this, false)

                // Démarrer l'activité d'onboarding
                val intent = Intent(this, OnboardingActivity::class.java)
                startActivity(intent)

                // Fermer l'activité des paramètres
                finish()

            } catch (e: Exception) {
                // Analytics: suivre l'erreur de replay onboarding
                AnalyticsManager.logUserAction(
                    action = "replay_onboarding_error",
                    category = "settings",
                    additionalParams = mapOf("error_type" to e.javaClass.simpleName)
                )

                CrashlyticsManager.log("Erreur lors du replay de l'onboarding: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_replay_onboarding")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors du redémarrage de l'onboarding", Toast.LENGTH_SHORT).show()
            }
        }

        // CardView pour rejouer le guide d'ajout du premier produit
        replayAddProductGuideCard.setOnClickListener {
            val guide = com.dedoware.shoopt.utils.AddFirstProductGuide(this)
            guide.resetGuide()
            Toast.makeText(this, getString(R.string.replay_add_product_guide_description), Toast.LENGTH_SHORT).show()
            // Lancer MainActivity avec un extra pour démarrer le guide
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Removed FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("EXTRA_START_ADD_PRODUCT_GUIDE", true)
            startActivity(intent)
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
                if (position >= 0) {
                    val selectedCurrencyCode = currencyList[position].code

                    // Analytics: suivre le changement de devise
                    AnalyticsManager.logUserAction(
                        action = "change_preference",
                        category = "settings",
                        additionalParams = mapOf(
                            "preference_type" to "currency",
                            "selected_value" to selectedCurrencyCode
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
            if (currencyIndex != -1) {
                selectedCurrency = currencyList[currencyIndex].code
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

    private fun loadCurrenciesFromJson(): List<Currency> {
        val currencies = mutableListOf<Currency>()
        try {
            val inputStream: InputStream = resources.openRawResource(R.raw.currencies)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                val code = jsonObject.getString("code")
                currencies.add(Currency(name, code))
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Error loading currencies: ${e.message}")
            CrashlyticsManager.logException(e)
        }
        return currencies
    }

    data class Currency(val name: String, val code: String)

    override fun onResume() {
        super.onResume()
        // Suivre le temps passé sur l'écran des paramètres
        AnalyticsManager.logScreenView("settings_screen", "SettingsActivity")
    }
}
