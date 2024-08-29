package com.dedoware.shoopt

import android.app.Application
import com.dedoware.shoopt.persistence.ShooptRoomDatabase

class ShooptApplication : Application() {

    val database: ShooptRoomDatabase by lazy {
        ShooptRoomDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize other components here if necessary
    }
}
