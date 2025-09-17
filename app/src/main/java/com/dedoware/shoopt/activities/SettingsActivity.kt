package com.dedoware.shoopt.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ui.settings.CurrencySelectionDialog
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.CurrencyManager
import com.dedoware.shoopt.utils.UserPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var userPreferences: UserPreferences
    private lateinit var currencyManager: CurrencyManager

    // UI Elements
    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var themeLightRadio: RadioButton
    private lateinit var themeDarkRadio: RadioButton
    private lateinit var themeSystemRadio: RadioButton
    private lateinit var currencyContainer: CardView
    private lateinit var currencyValue: TextView
    private lateinit var saveButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var replayOnboardingCard: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            // Suivi de la vue d'écran pour l'analyse
            AnalyticsManager.logScreenView("settings_screen", "SettingsActivity")

            try {
                userPreferences = UserPreferences(this)
                currencyManager = CurrencyManager.getInstance(this)
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
            themeRadioGroup = findViewById(R.id.theme_radio_group)
            themeLightRadio = findViewById(R.id.theme_light)
            themeDarkRadio = findViewById(R.id.theme_dark)
            themeSystemRadio = findViewById(R.id.theme_system)
            currencyContainer = findViewById(R.id.currencyContainer)
            currencyValue = findViewById(R.id.currencyValue)
            saveButton = findViewById(R.id.save_settings_button)
            backButton = findViewById(R.id.back_IB)
            replayOnboardingCard = findViewById(R.id.replay_onboarding_card)

            // Observer les changements de devise depuis le CurrencyManager
            currencyManager.currentCurrency.observe(this) { currency ->
                currencyValue.text = "${currency.code} - ${currency.name}"
            }

            // Analytics: suivre les options disponibles
            AnalyticsManager.logUserAction(
                action = "available_options",
                category = "settings",
                additionalParams = mapOf(
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

        // La devise est automatiquement mise à jour via l'observer du CurrencyManager

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

        // Container de devise - ouvre le dialogue de sélection
        currencyContainer.setOnClickListener {
            try {
                // Analytics: suivre l'ouverture du dialogue de devise
                AnalyticsManager.logUserAction(
                    action = "open_currency_dialog",
                    category = "settings",
                    additionalParams = mapOf("current_currency" to userPreferences.currency)
                )

                val currentCurrency = userPreferences.currency ?: "EUR"
                val dialog = CurrencySelectionDialog.newInstance(currentCurrency)
                dialog.setOnCurrencySelectedListener(object : CurrencySelectionDialog.OnCurrencySelectedListener {
                    override fun onCurrencySelected(currencyCode: String) {
                        // Analytics: suivre la sélection de devise
                        AnalyticsManager.logUserAction(
                            action = "currency_selected",
                            category = "settings",
                            additionalParams = mapOf(
                                "previous_currency" to userPreferences.currency,
                                "new_currency" to currencyCode
                            )
                        )

                        currencyManager.setCurrency(currencyCode)
                    }
                })
                dialog.show(supportFragmentManager, "CurrencySelectionDialog")
            } catch (e: Exception) {
                // Analytics: suivre l'erreur d'ouverture du dialogue
                AnalyticsManager.logUserAction(
                    action = "currency_dialog_error",
                    category = "settings",
                    additionalParams = mapOf("error_type" to e.javaClass.simpleName)
                )

                CrashlyticsManager.log("Erreur lors de l'ouverture du dialogue de devise: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'ouverture de la sélection de devise", Toast.LENGTH_SHORT).show()
            }
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

                // RESET COMPLET : Réinitialiser TOUT l'état d'onboarding
                UserPreferences.setOnboardingCompleted(this, false)
                UserPreferences.resetAllSpotlights(this)

                // Ajouter un flag spécial pour indiquer qu'on vient de faire un replay
                val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("onboarding_replay_requested", true).apply()

                // Log pour debug
                CrashlyticsManager.log("Settings: Replay onboarding - Reset completed, starting OnboardingActivity")

                // Démarrer l'activité d'onboarding
                val intent = Intent(this, OnboardingActivity::class.java)
                // Ajouter un flag pour indiquer que c'est un replay
                intent.putExtra("is_replay", true)
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

            // La devise est automatiquement sauvegardée par le CurrencyManager

            // Analytics: suivre les préférences sauvegardées
            AnalyticsManager.logUserAction(
                action = "save_preferences",
                category = "settings",
                additionalParams = mapOf(
                    "theme_mode" to themeModeString,
                    "currency" to userPreferences.currency
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
