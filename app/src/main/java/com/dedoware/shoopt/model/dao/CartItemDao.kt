package com.dedoware.shoopt.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.model.CartItemEntity

@Dao
interface CartItemDao {

    @Query("SELECT * FROM cart_items")
    suspend fun getAllCartItems(): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE id = :id")
    suspend fun getCartItemById(id: Long): CartItemEntity?

    @Insert
    suspend fun insert(cartItem: CartItemEntity): Long

    @Update
    suspend fun update(cartItem: CartItemEntity): Int

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()
}
