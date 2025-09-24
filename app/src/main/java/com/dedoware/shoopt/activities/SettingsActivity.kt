package com.dedoware.shoopt.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.ui.settings.CurrencySelectionDialog
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.CurrencyManager
import com.dedoware.shoopt.utils.UserPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var userPreferences: UserPreferences
    private lateinit var currencyManager: CurrencyManager
    private lateinit var analyticsService: AnalyticsService

    // Propriété d'extension pour faciliter l'accès à l'état du consentement analytics
    private var UserPreferences.isAnalyticsEnabled: Boolean
        get() = UserPreferences.isAnalyticsEnabled(this@SettingsActivity)
        set(value) = UserPreferences.setAnalyticsEnabled(this@SettingsActivity, value)

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
    private lateinit var analyticsSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            // Suivi de la vue d'écran pour l'analyse
            AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("settings_screen", "SettingsActivity")

            try {
                userPreferences = UserPreferences.getInstance(this)
                currencyManager = CurrencyManager.getInstance(this)
                analyticsService = AnalyticsService.getInstance(this)
            } catch (e: Exception) {
                // Analytics: suivre l'erreur d'initialisation des préférences
                val initErrParams = Bundle().apply {
                    putString("action", "initialization_error")
                    putString("category", "settings")
                    putString("component", "user_preferences")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", initErrParams)

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
                val uiInitErrParams = Bundle().apply {
                    putString("action", "initialization_error")
                    putString("category", "settings")
                    putString("component", "ui_elements")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", uiInitErrParams)

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
                val loadPrefErrParams = Bundle().apply {
                    putString("action", "initialization_error")
                    putString("category", "settings")
                    putString("component", "load_preferences")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", loadPrefErrParams)

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
                val setupListenersErrParams = Bundle().apply {
                    putString("action", "initialization_error")
                    putString("category", "settings")
                    putString("component", "setup_listeners")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", setupListenersErrParams)

                CrashlyticsManager.log("Erreur lors de la configuration des listeners: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "settings_setup_listeners")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de la configuration des contrôles", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Analytics: suivre les erreurs critiques
            val criticalErrParams = Bundle().apply {
                putString("action", "critical_error")
                putString("category", "app_initialization")
                putString("activity", "SettingsActivity")
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", criticalErrParams)

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
            analyticsSwitch = findViewById(R.id.analytics_switch)

            // Observer les changements de devise depuis le CurrencyManager
            currencyManager.currentCurrency.observe(this) { currency ->
                currencyValue.text = "${currency.code} - ${currency.name}"
            }

            // Initialiser l'état du switch d'analytics
            analyticsSwitch.isChecked = userPreferences.isAnalyticsEnabled

            // Analytics: suivre les options disponibles
            val availableOptionsParams = Bundle().apply {
                putString("action", "available_options")
                putString("category", "settings")
                putInt("theme_options", 3)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", availableOptionsParams)
        } catch (e: Exception) {
            // Log and handle initialization errors
            val uiInitErr2Params = Bundle().apply {
                putString("action", "ui_initialization_error")
                putString("category", "settings")
                putString("error", e.message ?: "Unknown error")
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", uiInitErr2Params)
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
        val currentPrefParams = Bundle().apply {
            putString("action", "current_preferences")
            putString("category", "settings")
            putString("theme_mode", themeMode)
            putString("currency", userPreferences.currency)
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", currentPrefParams)
    }

    private fun setupListeners() {
        // Bouton de sauvegarde
        saveButton.setOnClickListener {
            try {
                // Analytics: suivre le clic sur le bouton de sauvegarde
                val saveClickParams = Bundle().apply {
                    putString("action", "click")
                    putString("category", "settings")
                    putString("button", "save_settings")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", saveClickParams)

                val startTime = System.currentTimeMillis()
                savePreferences()
                val duration = System.currentTimeMillis() - startTime

                // Analytics: suivre la performance de la sauvegarde
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("settings_save_performance", Bundle().apply { putLong("duration_ms", duration) })

                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                // Analytics: suivre l'échec de sauvegarde
                val saveErrParams = Bundle().apply {
                    putString("action", "save_error")
                    putString("category", "settings")
                    putString("error_type", e.javaClass.simpleName)
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", saveErrParams)

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
            val backClickParams = Bundle().apply {
                putString("action", "click")
                putString("category", "settings")
                putString("button", "back")
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", backClickParams)

            finish()
        }

        // Container de devise - ouvre le dialogue de sélection
        currencyContainer.setOnClickListener {
            try {
                // Analytics: suivre l'ouverture du dialogue de devise
                val openCurrencyParams = Bundle().apply {
                    putString("action", "open_currency_dialog")
                    putString("category", "settings")
                    putString("current_currency", userPreferences.currency)
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", openCurrencyParams)

                val currentCurrency = userPreferences.currency ?: "EUR"
                val dialog = CurrencySelectionDialog.newInstance(currentCurrency)
                dialog.setOnCurrencySelectedListener(object : CurrencySelectionDialog.OnCurrencySelectedListener {
                    override fun onCurrencySelected(currencyCode: String) {
                        // Analytics: suivre la sélection de devise
                        val currencySelectedParams = Bundle().apply {
                            putString("action", "currency_selected")
                            putString("category", "settings")
                            putString("previous_currency", userPreferences.currency)
                            putString("new_currency", currencyCode)
                        }
                        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", currencySelectedParams)

                        currencyManager.setCurrency(currencyCode)
                    }
                })
                dialog.show(supportFragmentManager, "CurrencySelectionDialog")
            } catch (e: Exception) {
                // Analytics: suivre l'erreur d'ouverture du dialogue
                val currencyDialogErrParams = Bundle().apply {
                    putString("action", "currency_dialog_error")
                    putString("category", "settings")
                    putString("error_type", e.javaClass.simpleName)
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", currencyDialogErrParams)

                CrashlyticsManager.log("Erreur lors de l'ouverture du dialogue de devise: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'ouverture de la sélection de devise", Toast.LENGTH_SHORT).show()
            }
        }

        // Bouton de replay de l'onboarding
        replayOnboardingCard.setOnClickListener {
            try {
                // Analytics: suivre le clic sur le bouton de replay onboarding
                val replayParams = Bundle().apply {
                    putString("action", "click")
                    putString("category", "settings")
                    putString("button", "replay_onboarding")
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", replayParams)

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
                val replayErrParams = Bundle().apply {
                    putString("action", "replay_onboarding_error")
                    putString("category", "settings")
                    putString("error_type", e.javaClass.simpleName)
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", replayErrParams)

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
            val themeChangeParams = Bundle().apply {
                putString("action", "change_preference")
                putString("category", "settings")
                putString("preference_type", "theme")
                putString("selected_value", selectedTheme)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", themeChangeParams)
        }

        // Listener pour le switch d'analytics
        analyticsSwitch.setOnCheckedChangeListener { _, isChecked ->
            userPreferences.isAnalyticsEnabled = isChecked
            if (isChecked) {
                // Activer le suivi analytics
                analyticsService.enableTracking()
                Toast.makeText(this, "Suivi analytics activé", Toast.LENGTH_SHORT).show()
            } else {
                // Désactiver le suivi analytics
                analyticsService.disableTracking()
                Toast.makeText(this, "Suivi analytics désactivé", Toast.LENGTH_SHORT).show()
            }

            // Analytics: suivre le changement de préférence de consentement
            val consentParams = Bundle().apply {
                putString("action", "change_consent")
                putString("category", "settings")
                putString("consent_type", "analytics")
                putString("granted", isChecked.toString())
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", consentParams)
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
            val saveParams = Bundle().apply {
                putString("action", "save_preferences")
                putString("category", "settings")
                putString("theme_mode", themeModeString)
                putString("currency", userPreferences.currency)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", saveParams)

            // Application immédiate du thème
            try {
                userPreferences.applyTheme()
            } catch (e: Exception) {
                // Analytics: suivre l'échec d'application du thème
                val themeAppErrParams = Bundle().apply {
                    putString("action", "theme_application_error")
                    putString("category", "settings")
                    putString("theme_mode", themeModeString)
                    putString("error_type", e.javaClass.simpleName)
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", themeAppErrParams)

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
        AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("settings_screen", "SettingsActivity")
    }
}
