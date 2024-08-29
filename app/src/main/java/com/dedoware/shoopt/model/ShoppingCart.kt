package com.dedoware.shoopt.model

import androidx.room.Embedded
import com.google.firebase.database.Exclude
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

data class ShoppingCart(
    var id: Int = 0, // ID to match with Room entity
    var products: MutableList<CartItem> = mutableListOf() // List of products in the cart with dynamic quantities
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "products" to products
        )
    }
}

// Room-compatible ShoppingCart
@Entity(tableName = "shopping_cart")
data class ShoppingCartEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Primary key for Room
    val cartName: String = "default_cart"  // Example field to differentiate carts (optional)
)

data class ShoppingCartWithItems(
    @Embedded val shoppingCartEntity: ShoppingCartEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "cartId"
    )
    val items: List<CartItemEntity>
)

fun ShoppingCart.toShoppingCartWithItems(): ShoppingCartWithItems {
    val shoppingCartEntity = ShoppingCartEntity() // Or pass a cart name if needed
    val cartItemEntities = this.products.map { CartItemEntity.fromCartItem(it, shoppingCartEntity.id) }
    return ShoppingCartWithItems(shoppingCartEntity, cartItemEntities)
}

fun ShoppingCartWithItems.toShoppingCart(products: List<Product>): ShoppingCart {
    val cartItems = this.items.map { cartItemEntity ->
        val product = products.find { it.id == cartItemEntity.productId }
            ?: throw IllegalStateException("Product not found for ID: ${cartItemEntity.productId}")
        cartItemEntity.toCartItem(product)
    }.toMutableList()
    return ShoppingCart(products = cartItems)
}
