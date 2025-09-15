package com.dedoware.shoopt.utils

import android.content.Context
import com.dedoware.shoopt.ShooptApplication
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
 */
fun Double.convertToCurrentCurrency(context: Context, fromCurrency: String): Double {
    return context.getCurrencyManager().convertToCurrentCurrency(this, fromCurrency)
}

/**
 * Extension pour arrondir un prix à 2 décimales
 */
fun Double.roundPrice(decimals: Int = 2): Double {
    return BigDecimal(this).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()
}
