package com.dedoware.shoopt.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dedoware.shoopt.model.CartItemEntity
import com.dedoware.shoopt.model.ShoppingCartEntity
import com.dedoware.shoopt.model.ShoppingCartWithItems

@Dao
interface ShoppingCartDao {

    @Transaction
    @Query("SELECT * FROM shopping_cart WHERE id = :id LIMIT 1")
    suspend fun getShoppingCartWithItems(id: Int): ShoppingCartWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCart(cart: ShoppingCartEntity): Long

    @Query("DELETE FROM cart_item WHERE cartId = :cartId")
    suspend fun deleteCartItemsByCartId(cartId: Int)

    @Update
    suspend fun updateCart(cart: ShoppingCartEntity)

    @Insert
    suspend fun insertCartItems(cartItems: List<CartItemEntity>)

    @Transaction
    suspend fun insertShoppingCart(shoppingCartWithItems: ShoppingCartWithItems) {
        val cartId = insertCart(shoppingCartWithItems.shoppingCartEntity)
        shoppingCartWithItems.items.forEach { it.cartId = cartId.toInt() }
        insertCartItems(shoppingCartWithItems.items)
    }

    @Transaction
    suspend fun updateShoppingCart(shoppingCartWithItems: ShoppingCartWithItems) {
        // Update the cart entity
        updateCart(shoppingCartWithItems.shoppingCartEntity)

        // Clear existing items for the cart
        deleteCartItemsByCartId(shoppingCartWithItems.shoppingCartEntity.id)

        // Insert the new/updated items
        insertCartItems(shoppingCartWithItems.items)
    }
}
