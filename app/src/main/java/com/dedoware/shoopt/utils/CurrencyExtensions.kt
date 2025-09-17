package com.dedoware.shoopt.utils

import android.content.Context
import com.dedoware.shoopt.ShooptApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Extensions Kotlin pour faciliter l'utilisation du gestionnaire de devises
 * dans toute l'application
 */

/**
 * Récupère l'instance du gestionnaire de devises depuis n'importe quel Context
 */
fun Context.getCurrencyManager(): CurrencyManager {
    return (applicationContext as ShooptApplication).currencyManager
}

/**
 * Extension pour formater facilement un Double en prix avec devise
 */
fun Double.formatAsPrice(context: Context): String {
    return context.getCurrencyManager().formatPrice(this)
}

/**
 * Extension pour formater facilement un BigDecimal en prix avec devise
 */
fun BigDecimal.formatAsPrice(context: Context): String {
    return context.getCurrencyManager().formatPrice(this.toDouble())
}

/**
 * Extension pour convertir un prix d'une devise à la devise actuelle
 * Utilise la méthode à callback pour la conversion asynchrone
 */
fun Double.convertToCurrentCurrency(context: Context, fromCurrency: String, callback: (Double, Boolean) -> Unit) {
    context.getCurrencyManager().convertToCurrentCurrency(this, fromCurrency, callback)
}

/**
 * Version suspend de la conversion
 * À utiliser dans les contextes Coroutine
 */
suspend fun Double.convertToCurrentCurrencySuspend(context: Context, fromCurrency: String): Double {
    return context.getCurrencyManager().convertToCurrentCurrencySuspend(this, fromCurrency)
}

/**
 * Extension pour formater un prix après conversion
 * Fonction pratique qui convertit puis formate dans un seul appel
 */
fun Double.convertAndFormatAsPrice(context: Context, fromCurrency: String, callback: (String) -> Unit) {
    this.convertToCurrentCurrency(context, fromCurrency) { convertedPrice, success ->
        val formattedPrice = if (success) {
            convertedPrice.formatAsPrice(context)
        } else {
            // En cas d'échec, formatter le prix original mais avec la devise actuelle
            this.formatAsPrice(context)
        }
        callback(formattedPrice)
    }
}

/**
 * Extension pour convertir et formatter une liste de prix en une seule opération
 * Très utile pour les adapters de recyclerview
 */
fun List<Pair<Double, String>>.convertAndFormatAllPrices(
    context: Context,
    scope: CoroutineScope,
    onComplete: (List<String>) -> Unit
) {
    val currencyManager = context.getCurrencyManager()
    val results = mutableListOf<String>()
    
    scope.launch {
        this@convertAndFormatAllPrices.forEach { (price, fromCurrency) ->
            val convertedPrice = try {
                currencyManager.convertToCurrentCurrencySuspend(price, fromCurrency)
            } catch (e: Exception) {
                price // En cas d'erreur, utiliser le prix original
            }
            results.add(currencyManager.formatPrice(convertedPrice))
        }
        
        withContext(Dispatchers.Main) {
            onComplete(results)
        }
    }
}

/**
 * Extension pour arrondir un prix à 2 décimales
 */
fun Double.roundPrice(decimals: Int = 2): Double {
    return BigDecimal(this).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()
}
