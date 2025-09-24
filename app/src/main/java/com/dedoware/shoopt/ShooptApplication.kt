package com.dedoware.shoopt

import android.app.Application
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.utils.CurrencyManager

/**
 * Classe Application principale pour Shoopt.
 * Gère les initialisations globales comme Firebase Analytics.
 */
class ShooptApplication : Application() {

    // Base de données Room pour l'application
    val database by lazy { ShooptRoomDatabase.getDatabase(this) }

    // Gestionnaire de devises pour l'application
    val currencyManager by lazy { CurrencyManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        // Initialiser le service d'analytics
        AnalyticsService.getInstance(applicationContext)
    }
}
