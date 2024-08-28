package com.dedoware.shoopt.persistence

import CartItem
import ShoppingCart
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseProductRepository : IProductRepository {
    private val database = Firebase.database.reference
    private val productsRef = database.child("products")
    private val shopsRef = database.child("shops")
    private val cartRef = database.child("shoppingCart")

    override suspend fun getUniqueId(): String? = withContext(Dispatchers.IO) {
        productsRef.push().key
    }

    override suspend fun getAll(): List<Product> = withContext(Dispatchers.IO) {
        val snapshot = productsRef.get().await()
        snapshot.children.mapNotNull { it.getValue(Product::class.java) }
    }

    override suspend fun getById(id: String): Product? = withContext(Dispatchers.IO) {
        val snapshot = productsRef.child(id).get().await()
        snapshot.getValue(Product::class.java)
    }

    override suspend fun insert(product: Product): String = withContext(Dispatchers.IO) {
        val newRef = productsRef.push()
        newRef.setValue(product).await()
        newRef.key ?: throw Exception("Failed to insert product")
    }

    override suspend fun update(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            productsRef.child(product.id).setValue(product).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            productsRef.child(id).removeValue().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun deleteProduct(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete product from Realtime Database
            productsRef.child(product.id).removeValue().await()

            // Delete product picture from Firebase Storage
            val pictureRef = FirebaseStorage.getInstance().getReferenceFromUrl(product.pictureUrl)
            pictureRef.delete().await()

            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getShops(): List<String> = withContext(Dispatchers.IO) {
        val snapshot = shopsRef.get().await()
        snapshot.children.mapNotNull { it.child("name").getValue(String::class.java) }
    }

    override suspend fun addShop(shop: Shop): Boolean = withContext(Dispatchers.IO) {
        try {
            shopsRef.push().setValue(shop).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getShoppingCart(): ShoppingCart? = withContext(Dispatchers.IO) {
        val snapshot = cartRef.get().await()
        snapshot.getValue(ShoppingCart::class.java)
    }

    override suspend fun addProductToCart(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentCart = getShoppingCart() ?: ShoppingCart(mutableListOf())

            val existingCartItem = currentCart.products.find { it.product.barcode == product.barcode }
            if (existingCartItem != null) {
                existingCartItem.quantity += 1
            } else {
                currentCart.products.add(CartItem(product, 1))
            }

            cartRef.setValue(currentCart).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun clearShoppingCart(): Boolean = withContext(Dispatchers.IO) {
        try {
            cartRef.child("products").removeValue().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getProductByBarcode(barcode: Long): Product? = withContext(Dispatchers.IO) {
        val querySnapshot = productsRef.orderByChild("barcode").equalTo(barcode.toDouble()).get().await()
        querySnapshot.children.firstOrNull()?.getValue(Product::class.java)
    }
}