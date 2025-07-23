package com.dedoware.shoopt

import android.app.Application
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.firebase.FirebaseApp

class ShooptApplication : Application() {

    val database: ShooptRoomDatabase by lazy {
        ShooptRoomDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialisation de Firebase
        FirebaseApp.initializeApp(this)

        // Activation de la collecte des rapports de crash Crashlytics
        CrashlyticsManager.setCrashlyticsCollectionEnabled(true)

        // Initialize other components here if necessary
    }
}
