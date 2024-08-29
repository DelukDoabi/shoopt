package com.dedoware.shoopt.persistence

import com.dedoware.shoopt.model.*
import com.dedoware.shoopt.model.dao.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomProductRepository(
    private val productDao: ProductDao,
    private val shopDao: ShopDao,
    private val shoppingCartDao: ShoppingCartDao,
    private val cartItemDao: CartItemDao
) : IProductRepository {

    // Generates a unique ID (UUID can be used as a unique identifier)
    override suspend fun getUniqueId(): String? = withContext(Dispatchers.IO) {
        java.util.UUID.randomUUID().toString()
    }

    // Fetch all products from the local Room database
    override suspend fun getAll(): List<Product> = withContext(Dispatchers.IO) {
        productDao.getAll()
    }

    // Fetch a product by its ID
    override suspend fun getById(id: String): Product? = withContext(Dispatchers.IO) {
        productDao.getById(id)
    }

    // Insert a new product into the local Room database
    override suspend fun insert(product: Product): String = withContext(Dispatchers.IO) {
        productDao.insert(product)
        product.id  // Assuming `id` is set before insertion
    }

    // Update an existing product in the local Room database
    override suspend fun update(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            productDao.update(product)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Delete a product by its ID from the local Room database
    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            productDao.deleteById(id)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Delete a product entity (same as deleting by ID)
    override suspend fun deleteProduct(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            productDao.deleteById(product.id)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Get all shops
    override suspend fun getShops(): List<String> = withContext(Dispatchers.IO) {
        shopDao.getAllShops().map { it.name }
    }

    // Add a new shop
    override suspend fun addShop(shop: Shop): Boolean = withContext(Dispatchers.IO) {
        try {
            shopDao.insert(shop)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Get the shopping cart
    override suspend fun getShoppingCart(): ShoppingCart? = withContext(Dispatchers.IO) {
        val shoppingCartWithItems = shoppingCartDao.getShoppingCartWithItems(1)  // Assuming there's only one cart

        if (shoppingCartWithItems != null) {
            shoppingCartWithItems?.let {
                ShoppingCart(
                    products = it.items.map { cartItemEntity ->
                        val product = productDao.getById(cartItemEntity.productId)
                        product?.let { p -> cartItemEntity.toCartItem(p) } ?: return@map null
                    }.filterNotNull().toMutableList()
                )
            }
        } else {
            val newCartEntity = ShoppingCartEntity(id = 1)
            shoppingCartDao.insertCart(newCartEntity)
            ShoppingCart(id = newCartEntity.id)
        }
    }

    // Add a product to the shopping cart
    override suspend fun addProductToCart(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            val shoppingCartWithItems = shoppingCartDao.getShoppingCartWithItems(1)  // Assuming a single cart with ID 1
            val cartId = shoppingCartWithItems?.shoppingCartEntity?.id ?: 1  // Use existing cart ID or default to 1

            val cart = getShoppingCart()
            cart?.let {
                val existingItem = it.products.find { item -> item.product.id == product.id }
                if (existingItem != null) {
                    existingItem.quantity += 1
                    cartItemDao.update(CartItemEntity.fromCartItem(existingItem, cartId))
                } else {
                    val newItem = CartItem(product, 1)
                    cart.products.add(newItem)
                    cartItemDao.insert(CartItemEntity.fromCartItem(newItem, cartId))
                }
                saveShoppingCart(cart)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    // Clear all products from the shopping cart
    override suspend fun clearShoppingCart(): Boolean = withContext(Dispatchers.IO) {
        try {
            cartItemDao.clearCart()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Fetch a product by its barcode
    override suspend fun getProductByBarcode(barcode: Long): Product? = withContext(Dispatchers.IO) {
        productDao.getByBarcode(barcode)
    }

    // Helper method to save the shopping cart back to the database
    private suspend fun saveShoppingCart(cart: ShoppingCart) = withContext(Dispatchers.IO) {
        val shoppingCartEntity = ShoppingCartEntity(id = 1)  // Assuming there's only one cart
        val cartItems = cart.products.map { CartItemEntity.fromCartItem(it, 1).apply { cartId = shoppingCartEntity.id } }
        shoppingCartDao.updateShoppingCart(ShoppingCartWithItems(shoppingCartEntity, cartItems))
    }
}
