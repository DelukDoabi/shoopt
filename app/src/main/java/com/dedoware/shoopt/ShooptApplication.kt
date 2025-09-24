package com.dedoware.shoopt

import android.app.Application
import android.content.pm.ApplicationInfo
import com.dedoware.shoopt.utils.AnalyticsManager
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

        // Détecter si l'app est en mode debug (au runtime) sans dépendre de BuildConfig
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Initialiser le gestionnaire d'analytics via la façade ; cela initialise
        // AnalyticsService sous-jacent et respecte les préférences utilisateur.
        AnalyticsManager.initialize(applicationContext, isDebug = isDebug)
    }
}
