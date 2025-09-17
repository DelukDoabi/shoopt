package com.dedoware.shoopt.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dedoware.shoopt.model.ExchangeRateEntity
import com.dedoware.shoopt.model.dao.ExchangeRateDao
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Service de taux de change en temps réel pour l'application Shoopt
 * Utilise l'API ExchangeRate pour obtenir des taux de change à jour
 */
class ExchangeRateService private constructor(private val context: Context) {

    companion object {
        private const val API_KEY = "YOUR_API_KEY" // À remplacer par une vraie clé API
        private const val API_URL = "https://open.er-api.com/v6/latest/"
        private const val CACHE_EXPIRY_HOURS = 24 // Durée de validité du cache en heures

        @Volatile
        private var INSTANCE: ExchangeRateService? = null

        fun getInstance(context: Context): ExchangeRateService {
            return INSTANCE ?: synchronized(this) {
                val instance = ExchangeRateService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val exchangeRateDao by lazy {
        ShooptRoomDatabase.getDatabase(context).exchangeRateDao()
    }

    /**
     * Convertit un montant d'une devise à une autre
     * Utilise les données en cache si disponibles et récentes, sinon fait une requête API
     */
    suspend fun convertAmount(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return amount

        try {
            val rate = getExchangeRate(fromCurrency, toCurrency)
            return amount * rate
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la conversion de devise: ${e.message}")
            CrashlyticsManager.logException(e)
            _error.postValue("Impossible de convertir ${fromCurrency} vers ${toCurrency}")
            return amount // En cas d'erreur, retourne le montant original
        }
    }

    /**
     * Récupère le taux de change entre deux devises
     */
    private suspend fun getExchangeRate(fromCurrency: String, toCurrency: String): Double {
        // Vérifier d'abord dans le cache
        val cacheEntry = exchangeRateDao.getExchangeRate(fromCurrency)

        if (cacheEntry != null && isCacheValid(cacheEntry.timestamp)) {
            // Cache valide, utiliser les données existantes
            val rates = JSONObject(cacheEntry.ratesJson)
            if (rates.has(toCurrency)) {
                return rates.getDouble(toCurrency)
            }
        }

        // Sinon faire une requête API
        return fetchExchangeRatesFromApi(fromCurrency, toCurrency)
    }

    /**
     * Vérifie si le cache est encore valide
     */
    private fun isCacheValid(timestamp: Long): Boolean {
        val expiryTime = timestamp + TimeUnit.HOURS.toMillis(CACHE_EXPIRY_HOURS.toLong())
        return System.currentTimeMillis() < expiryTime
    }

    /**
     * Récupère les taux de change depuis l'API
     */
    private suspend fun fetchExchangeRatesFromApi(baseCurrency: String, targetCurrency: String): Double {
        _isLoading.postValue(true)

        try {
            val request = Request.Builder()
                .url("${API_URL}${baseCurrency}")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                throw Exception("API error: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonObject = JSONObject(responseBody)

            if (jsonObject.has("rates")) {
                val rates = jsonObject.getJSONObject("rates")

                // Sauvegarder les taux dans le cache
                val exchangeRateEntity = ExchangeRateEntity(
                    baseCurrency = baseCurrency,
                    ratesJson = rates.toString(),
                    timestamp = System.currentTimeMillis()
                )
                exchangeRateDao.insert(exchangeRateEntity)

                // Retourner le taux demandé
                if (rates.has(targetCurrency)) {
                    val rate = rates.getDouble(targetCurrency)
                    _error.postValue(null)
                    return rate
                } else {
                    throw Exception("Currency $targetCurrency not found in rates")
                }
            } else {
                throw Exception("Invalid API response format")
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la récupération des taux de change: ${e.message}")
            CrashlyticsManager.logException(e)
            _error.postValue("Erreur réseau: ${e.message}")
            throw e
        } finally {
            _isLoading.postValue(false)
        }
    }

    /**
     * Rafraîchit manuellement les taux de change pour une devise de base
     */
    fun refreshExchangeRates(baseCurrency: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch {
            try {
                fetchExchangeRatesFromApi(baseCurrency, "USD") // "USD" est juste utilisé comme exemple
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
}
