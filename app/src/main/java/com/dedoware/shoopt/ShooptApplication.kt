package com.dedoware.shoopt

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ShooptApplication : Application() {

    val database: ShooptRoomDatabase by lazy {
        ShooptRoomDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialisation de Firebase
        FirebaseApp.initializeApp(this)

        // Configuration de Crashlytics avec identification de l'environnement
        setupCrashlytics()

        // Initialize other components here if necessary
    }

    private fun setupCrashlytics() {
        // Déterminer si nous sommes en mode debug
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Obtenir les informations de version depuis PackageManager
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toInt() ?: 0
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode ?: 0
        }

        // Configurer Crashlytics pour identifier clairement l'environnement
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)  // Activer globalement, mais les rapports ne seront pas envoyés en debug selon manifestPlaceholders

            // Ajouter des métadonnées pour filtrer les rapports
            setCustomKey("is_debug_build", isDebug)
            setCustomKey("app_version", versionName)
            setCustomKey("version_code", versionCode)

            // Préfixer les utilisateurs en debug pour faciliter le filtrage
            if (isDebug) {
                setUserId("DEBUG-USER")
                log("Application lancée en mode DEBUG")
            }
        }

        // Déléguer à notre gestionnaire personnalisé
        CrashlyticsManager.setCrashlyticsCollectionEnabled(true)
    }
}
