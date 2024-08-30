package com.dedoware.shoopt.persistence

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.model.CartItemEntity
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop
import com.dedoware.shoopt.model.ShoppingCart
import com.dedoware.shoopt.model.ShoppingCartEntity
import com.dedoware.shoopt.model.ShoppingList
import com.dedoware.shoopt.model.dao.CartItemDao
import com.dedoware.shoopt.model.dao.ProductDao
import com.dedoware.shoopt.model.dao.ShopDao
import com.dedoware.shoopt.model.dao.ShoppingCartDao
import com.dedoware.shoopt.model.dao.ShoppingListDao

@Database(
    version = 1,
    entities = [Product::class, Shop::class, ShoppingCartEntity::class, CartItemEntity::class, ShoppingList::class]
)
abstract class ShooptRoomDatabase : RoomDatabase() {  // Renamed class
    abstract fun productDao(): ProductDao
    abstract fun shopDao(): ShopDao
    abstract fun shoppingCartDao(): ShoppingCartDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun cartItemDao(): CartItemDao

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
