package com.dedoware.shoopt.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dedoware.shoopt.R
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Gestionnaire moderne des devises pour l'application Shoopt
 * Charge toutes les devises disponibles depuis currencies.json
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

    private val userPreferences = UserPreferences(context)
    private val _availableCurrencies = MutableLiveData<List<CurrencyInfo>>()
    val availableCurrencies: LiveData<List<CurrencyInfo>> = _availableCurrencies

    private val _currentCurrency = MutableLiveData<CurrencyInfo>()
    val currentCurrency: LiveData<CurrencyInfo> = _currentCurrency

    init {
        loadCurrenciesFromJson()
        updateCurrentCurrency(userPreferences.currency ?: getDefaultCurrency())
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
            userPreferences.currency = currencyCode
            updateCurrentCurrency(currencyCode)
        }
    }

    private fun updateCurrentCurrency(currencyCode: String) {
        val currencies = _availableCurrencies.value ?: return
        val currency = currencies.find { it.code == currencyCode }
            ?: currencies.first()
        _currentCurrency.value = currency
    }

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
     * Convertit un prix d'une devise source vers la devise actuelle
     * Pour le moment, retourne le prix tel quel car nous n'avons pas de service de taux de change
     * TODO: Intégrer un service de taux de change en temps réel
     */
    fun convertToCurrentCurrency(price: Double, fromCurrency: String): Double {
        val currentCurrencyCode = _currentCurrency.value?.code ?: "USD"

        // Si c'est déjà la même devise, pas de conversion nécessaire
        if (fromCurrency == currentCurrencyCode) {
            return price
        }

        // Pour le moment, on retourne le prix tel quel
        // Dans une future version, on pourrait ajouter un service de conversion
        // en utilisant une API comme exchangerate-api.com ou fixer.io

        try {
            CrashlyticsManager.log("Conversion de devise demandée: $fromCurrency -> $currentCurrencyCode pour $price")

            // Placeholder pour la conversion réelle
            // return convertWithExchangeRate(price, fromCurrency, currentCurrencyCode)

            return price
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la conversion de devise: ${e.message}")
            CrashlyticsManager.logException(e)
            return price
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
