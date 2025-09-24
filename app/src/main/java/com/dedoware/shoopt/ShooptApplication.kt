package com.dedoware.shoopt

import android.app.Application
import android.content.pm.ApplicationInfo
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.utils.CurrencyManager

/**
 * Classe Application principale pour Shoopt.
 * Gère les initialisations globales comme Firebase Analytics.
 */
class ShooptApplication : Application() {

    companion object {
        lateinit var instance: ShooptApplication
            private set
    }

    // Base de données Room pour l'application
    val database by lazy { ShooptRoomDatabase.getDatabase(this) }

    // Gestionnaire de devises pour l'application
    val currencyManager by lazy { CurrencyManager.getInstance(this) }

    // Global AnalyticsService instance accessible via ShooptApplication.instance.analyticsService
    val analyticsService by lazy { AnalyticsService.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Détecter si l'app est en mode debug (au runtime) sans dépendre de BuildConfig
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Initialiser le service d'analytics global et respecter le mode debug
        if (isDebug) {
            analyticsService.setAnalyticsCollectionEnabled(false)
        }
    }
}
