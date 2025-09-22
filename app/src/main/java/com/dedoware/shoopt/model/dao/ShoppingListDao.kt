package com.dedoware.shoopt.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dedoware.shoopt.model.ShoppingList

@Dao
interface ShoppingListDao {
    @Query("SELECT content FROM shopping_lists WHERE dbRefKey = :dbRefKey")
    suspend fun getShoppingList(dbRefKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShoppingList(shoppingList: ShoppingList): Long

    @Query("SELECT COUNT(*) FROM shopping_lists")
    suspend fun getShoppingListsCount(): Int

    @Query("SELECT * FROM shopping_lists")
    suspend fun getAllShoppingLists(): List<ShoppingList>
}