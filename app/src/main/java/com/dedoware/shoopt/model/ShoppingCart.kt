import com.google.firebase.database.Exclude

data class ShoppingCart(
    var products: MutableList<CartItem> = mutableListOf() // List of products in the cart with dynamic quantities
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "products" to products
        )
    }
}
