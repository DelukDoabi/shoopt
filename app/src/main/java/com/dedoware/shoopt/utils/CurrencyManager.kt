package com.dedoware.shoopt.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dedoware.shoopt.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Gestionnaire moderne des devises pour l'application Shoopt
 * Charge toutes les devises disponibles depuis currencies.json
 * et gère la conversion entre les différentes devises
 */
class CurrencyManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CurrencyManager? = null

        fun getInstance(context: Context): CurrencyManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CurrencyManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val userPreferences = UserPreferences.getInstance(context)
    private val exchangeRateService = ExchangeRateService.getInstance(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _availableCurrencies = MutableLiveData<List<CurrencyInfo>>()
    val availableCurrencies: LiveData<List<CurrencyInfo>> = _availableCurrencies

    private val _currentCurrency = MutableLiveData<CurrencyInfo>()
    val currentCurrency: LiveData<CurrencyInfo> = _currentCurrency
    
    private val _conversionInProgress = MutableLiveData<Boolean>(false)
    val conversionInProgress: LiveData<Boolean> = _conversionInProgress
    
    private val _conversionError = MutableLiveData<String?>(null)
    val conversionError: LiveData<String?> = _conversionError

    // LiveData qui émet un événement à chaque changement de devise
    private val _currencyChangeEvent = MutableLiveData<String>()
    val currencyChangeEvent: LiveData<String> = _currencyChangeEvent

    init {
        loadCurrenciesFromJson()
        updateCurrentCurrency(userPreferences.currency ?: getDefaultCurrency())
        // Préchargement des taux de change pour la devise actuelle
        preloadExchangeRates()
    }

    // Fonction qui précharge les taux de change pour la devise actuelle
    private fun preloadExchangeRates() {
        val currentCurrencyCode = currentCurrency.value?.code ?: getDefaultCurrency()
        coroutineScope.launch {
            try {
                exchangeRateService.refreshExchangeRates(currentCurrencyCode) { success ->
                    if (!success) {
                        CrashlyticsManager.log("Échec du préchargement des taux de change pour $currentCurrencyCode")
                    }
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du préchargement des taux de change: ${e.message}")
                CrashlyticsManager.logException(e)
            }
        }
    }

    private fun loadCurrenciesFromJson() {
        try {
            val currencies = mutableListOf<CurrencyInfo>()
            val inputStream = context.resources.openRawResource(R.raw.currencies)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                val code = jsonObject.getString("code")

                // Extraire le symbole du nom (entre parenthèses)
                val symbol = extractSymbolFromName(name, code)

                currencies.add(CurrencyInfo(code, name, symbol))
            }

            _availableCurrencies.value = currencies.sortedBy { it.name }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du chargement des devises: ${e.message}")
            CrashlyticsManager.logException(e)

            // Fallback avec quelques devises de base
            _availableCurrencies.value = listOf(
                CurrencyInfo("EUR", "Euro (€)", "€"),
                CurrencyInfo("USD", "US Dollar ($)", "$"),
                CurrencyInfo("GBP", "British Pound (£)", "£")
            )
        }
    }

    private fun extractSymbolFromName(name: String, code: String): String {
        // Extraire le symbole entre parenthèses dans le nom
        val regex = "\\(([^)]+)\\)".toRegex()
        val match = regex.find(name)
        return match?.groupValues?.get(1) ?: code
    }

    private fun getDefaultCurrency(): String {
        val country = Locale.getDefault().country
        val defaultCurrencyByCountry = mapOf(
            "FR" to "EUR", "BE" to "EUR", "DE" to "EUR", "IT" to "EUR", "ES" to "EUR",
            "PT" to "EUR", "NL" to "EUR", "LU" to "EUR", "IE" to "EUR", "FI" to "EUR",
            "AT" to "EUR", "GR" to "EUR", "US" to "USD", "GB" to "GBP", "CA" to "CAD",
            "CH" to "CHF", "JP" to "JPY", "CN" to "CNY", "IN" to "INR", "BR" to "BRL",
            "RU" to "RUB", "AU" to "AUD", "MX" to "MXN", "SE" to "SEK", "NO" to "NOK",
            "DK" to "DKK", "PL" to "PLN", "TN" to "TND", "MA" to "MAD", "DZ" to "DZD",
            "EG" to "EGP", "SA" to "SAR", "AE" to "AED", "QA" to "QAR", "KW" to "KWD",
            "OM" to "OMR", "BH" to "BHD", "JO" to "JOD", "LB" to "LBP"
        )
        return defaultCurrencyByCountry[country] ?: "USD"
    }

    fun setCurrency(currencyCode: String) {
        val currencies = _availableCurrencies.value ?: return
        if (currencies.any { it.code == currencyCode }) {
            // Vérifier si la devise a vraiment changé
            val oldCurrencyCode = _currentCurrency.value?.code

            // Stocker la nouvelle devise
            userPreferences.currency = currencyCode
            updateCurrentCurrency(currencyCode)

            // Précharger les taux de change pour la nouvelle devise
            if (oldCurrencyCode != currencyCode) {
                preloadExchangeRates()
                // Notifier les observateurs que la devise a changé
                _currencyChangeEvent.value = currencyCode
            }
        }
    }

    private fun updateCurrentCurrency(currencyCode: String) {
        val currencies = _availableCurrencies.value ?: return
        val currency = currencies.find { it.code == currencyCode }
            ?: currencies.first()
        _currentCurrency.value = currency
    }

    /**
     * Convertit un prix d'une devise source vers la devise actuelle
     * Utilise le service de taux de change pour faire la conversion
     */
    fun convertToCurrentCurrency(price: Double, fromCurrency: String, callback: (Double, Boolean) -> Unit) {
        val currentCurrencyCode = _currentCurrency.value?.code ?: "USD"

        // Si c'est déjà la même devise, pas de conversion nécessaire
        if (fromCurrency == currentCurrencyCode) {
            callback(price, true)
            return
        }

        _conversionInProgress.value = true
        _conversionError.value = null

        coroutineScope.launch {
            try {
                CrashlyticsManager.log("Conversion de devise demandée: $fromCurrency -> $currentCurrencyCode pour $price")
                
                val convertedPrice = exchangeRateService.convertAmount(price, fromCurrency, currentCurrencyCode)
                
                withContext(Dispatchers.Main) {
                    _conversionInProgress.value = false
                    callback(convertedPrice, true)
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la conversion de devise: ${e.message}")
                CrashlyticsManager.logException(e)
                
                withContext(Dispatchers.Main) {
                    _conversionInProgress.value = false
                    _conversionError.value = "Erreur de conversion: ${e.message}"
                    callback(price, false) // Retourne le prix original en cas d'erreur
                }
            }
        }
    }

    /**
     * Version synchrone simplifiée pour les cas où le callback n'est pas nécessaire
     * Retourne le prix original en cas d'erreur
     */
    suspend fun convertToCurrentCurrencySuspend(price: Double, fromCurrency: String): Double {
        val currentCurrencyCode = _currentCurrency.value?.code ?: "USD"

        // Si c'est déjà la même devise, pas de conversion nécessaire
        if (fromCurrency == currentCurrencyCode) {
            return price
        }

        return try {
            exchangeRateService.convertAmount(price, fromCurrency, currentCurrencyCode)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la conversion de devise: ${e.message}")
            CrashlyticsManager.logException(e)
            price // En cas d'erreur, retourne le prix original
        }
    }

    /**
     * Formate le prix avec la devise spécifiée ou la devise actuelle
     */
    fun formatPrice(price: Double, currencyCode: String? = null): String {
        val currency = if (currencyCode != null) {
            _availableCurrencies.value?.find { it.code == currencyCode }
        } else {
            _currentCurrency.value
        } ?: return String.format("%.2f", price)

        return try {
            val currencyInstance = Currency.getInstance(currency.code)
            val numberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
            numberFormat.currency = currencyInstance
            numberFormat.format(price)
        } catch (e: Exception) {
            String.format("%.2f %s", price, currency.symbol)
        }
    }

    /**
     * Obtient le code de la devise actuelle
     */
    fun getCurrentCurrencyCode(): String {
        return _currentCurrency.value?.code ?: "USD"
    }

    /**
     * Obtient les informations de la devise actuelle
     */
    fun getCurrentCurrencyInfo(): CurrencyInfo? {
        return _currentCurrency.value
    }

    data class CurrencyInfo(
        val code: String,
        val name: String,
        val symbol: String
    )
}
