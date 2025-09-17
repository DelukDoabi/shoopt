package com.dedoware.shoopt.persistence

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.model.CartItemEntity
import com.dedoware.shoopt.model.ExchangeRateEntity
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop
import com.dedoware.shoopt.model.ShoppingCart
import com.dedoware.shoopt.model.ShoppingCartEntity
import com.dedoware.shoopt.model.ShoppingList
import com.dedoware.shoopt.model.dao.CartItemDao
import com.dedoware.shoopt.model.dao.ExchangeRateDao
import com.dedoware.shoopt.model.dao.ProductDao
import com.dedoware.shoopt.model.dao.ShopDao
import com.dedoware.shoopt.model.dao.ShoppingCartDao
import com.dedoware.shoopt.model.dao.ShoppingListDao

@Database(
    version = 2, // Augmenter la version de la base de données car nous avons ajouté une nouvelle entité
    entities = [
        Product::class,
        Shop::class,
        ShoppingCartEntity::class,
        CartItemEntity::class,
        ShoppingList::class,
        ExchangeRateEntity::class // Ajout de notre nouvelle entité
    ],
    exportSchema = false // Empêcher l'exportation du schéma pour la simplicité
)
abstract class ShooptRoomDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun shopDao(): ShopDao
    abstract fun shoppingCartDao(): ShoppingCartDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun cartItemDao(): CartItemDao
    abstract fun exchangeRateDao(): ExchangeRateDao // Ajout de notre nouveau DAO

    companion object {
        @Volatile
        private var INSTANCE: ShooptRoomDatabase? = null

        fun getDatabase(context: Context): ShooptRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShooptRoomDatabase::class.java,
                    "shoopt_room_database"
                )
                    .fallbackToDestructiveMigration() // Utiliser cette option pour simplifier la migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
