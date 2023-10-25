import com.dedoware.shoopt.model.Product
import com.google.firebase.database.Exclude

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
