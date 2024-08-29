package com.dedoware.shoopt.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude

// Firebase CartItem (Existing)
data class CartItem(
    var product: Product = Product(),   // Reference to the product
    var quantity: Int = 0              // Dynamic quantity of the product in the cart
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "product" to product,
            "quantity" to quantity
        )
    }
}

// Room-compatible CartItem
@Entity(
    tableName = "cart_item",
    foreignKeys = [ForeignKey(
        entity = ShoppingCartEntity::class,
        parentColumns = ["id"],
        childColumns = ["cartId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["cartId"])]
)
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,  // Room-specific ID
    var cartId: Int,  // Foreign key to ShoppingCartEntity
    val productId: String = "",  // ID of the product
    val quantity: Int = 0       // Quantity of the product
) {
    // Convert CartItemEntity to CartItem
    fun toCartItem(product: Product): CartItem {
        return CartItem(
            product = product,
            quantity = quantity
        )
    }

    // Convert CartItem to CartItemEntity
    companion object {
        fun fromCartItem(cartItem: CartItem, cartId: Int): CartItemEntity {
            return CartItemEntity(
                cartId = cartId,
                productId = cartItem.product.id,
                quantity = cartItem.quantity
            )
        }
    }
}


