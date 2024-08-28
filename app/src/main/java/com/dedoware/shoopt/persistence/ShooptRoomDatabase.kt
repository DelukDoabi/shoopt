package com.dedoware.shoopt.persistence

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.dao.ProductDao

@Database(entities = [Product::class], version = 1)
abstract class ShooptRoomDatabase : RoomDatabase() {  // Renamed class
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile
        private var INSTANCE: ShooptRoomDatabase? = null  // Updated reference

        fun getDatabase(context: Context): ShooptRoomDatabase {  // Updated return type
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShooptRoomDatabase::class.java,  // Updated class reference
                    "shoopt_room_database"  // Database name
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
