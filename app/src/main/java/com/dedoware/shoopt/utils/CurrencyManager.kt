package com.dedoware.shoopt.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Gestionnaire moderne des devises pour l'application Shoopt
 * Permet de centraliser la gestion des devises, le formatage des prix
 * et la conversion entre devises
 */
class CurrencyManager private constructor(context: Context) {

    companion object {
        // Instance singleton
        @Volatile
        private var INSTANCE: CurrencyManager? = null

        fun getInstance(context: Context): CurrencyManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CurrencyManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // Devises supportées par l'application
        val SUPPORTED_CURRENCIES = listOf(
            CurrencyInfo("EUR", "Euro", "€", 1.0),
            CurrencyInfo("USD", "US Dollar", "$", 1.08),
            CurrencyInfo("GBP", "British Pound", "£", 0.85),
            CurrencyInfo("JPY", "Japanese Yen", "¥", 160.0),
            CurrencyInfo("CAD", "Canadian Dollar", "C$", 1.47),
            CurrencyInfo("CHF", "Swiss Franc", "Fr", 0.97),
            CurrencyInfo("AUD", "Australian Dollar", "A$", 1.65),
            CurrencyInfo("CNY", "Chinese Yuan", "¥", 7.84),
            CurrencyInfo("INR", "Indian Rupee", "₹", 90.23)
        )
    }

    // Preferences pour stocker les réglages de devise
    private val userPreferences = UserPreferences(context)

    // LiveData pour observer les changements de devise
    private val _currentCurrency = MutableLiveData<CurrencyInfo>()
    val currentCurrency: LiveData<CurrencyInfo> = _currentCurrency

    init {
        // Initialisation avec la devise actuelle des préférences
        updateCurrentCurrency(userPreferences.currency)
    }

    /**
     * Change la devise active dans l'application
     */
    fun setCurrency(currencyCode: String) {
        if (SUPPORTED_CURRENCIES.any { it.code == currencyCode }) {
            userPreferences.currency = currencyCode
            updateCurrentCurrency(currencyCode)
        }
    }

    /**
     * Met à jour la devise courante dans le LiveData
     */
    private fun updateCurrentCurrency(currencyCode: String) {
        val currency = SUPPORTED_CURRENCIES.find { it.code == currencyCode }
            ?: SUPPORTED_CURRENCIES.first()
        _currentCurrency.value = currency
    }

    /**
     * Formate un prix selon les conventions locales et la devise sélectionnée
     */
    fun formatPrice(price: Double, currencyCode: String? = null): String {
        val currency = if (currencyCode != null) {
            SUPPORTED_CURRENCIES.find { it.code == currencyCode }
        } else {
            _currentCurrency.value
        } ?: SUPPORTED_CURRENCIES.first()

        return try {
            // Utiliser la classe NumberFormat pour un formatage correct selon les conventions locales
            val currencyInstance = Currency.getInstance(currency.code)
            val numberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
            numberFormat.currency = currencyInstance
            numberFormat.format(price)
        } catch (e: Exception) {
            // Fallback simple en cas d'erreur
            String.format("%.2f %s", price, currency.symbol)
        }
    }

    /**
     * Convertit un prix d'une devise à une autre
     * @param amount Montant à convertir
     * @param fromCurrency Code de la devise source
     * @param toCurrency Code de la devise cible
     * @return Montant converti
     */
    fun convertCurrency(amount: Double, fromCurrency: String, toCurrency: String): Double {
        val sourceCurrency = SUPPORTED_CURRENCIES.find { it.code == fromCurrency }
            ?: SUPPORTED_CURRENCIES.first()
        val targetCurrency = SUPPORTED_CURRENCIES.find { it.code == toCurrency }
            ?: SUPPORTED_CURRENCIES.first()

        // Conversion via le taux de référence (ici l'Euro)
        val amountInEur = amount / sourceCurrency.rateFromEur
        return amountInEur * targetCurrency.rateFromEur
    }

    /**
     * Convertit un prix vers la devise actuellement sélectionnée
     */
    fun convertToCurrentCurrency(amount: Double, fromCurrency: String): Double {
        return convertCurrency(amount, fromCurrency, _currentCurrency.value?.code ?: "EUR")
    }

    /**
     * Classe représentant une devise supportée
     */
    data class CurrencyInfo(
        val code: String,
        val name: String,
        val symbol: String,
        val rateFromEur: Double // Taux de conversion par rapport à l'Euro (référence)
    )
}
