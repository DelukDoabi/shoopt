package com.dedoware.shoopt.persistence

import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop
import com.dedoware.shoopt.model.ShoppingCart

interface IProductRepository {
    suspend fun getUniqueId(): String?
    suspend fun getAll(): List<Product>
    suspend fun getById(id: String): Product?
    suspend fun insert(product: Product): String
    suspend fun update(product: Product): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun deleteProduct(product: Product): Boolean
    suspend fun getShops(): List<String>
    suspend fun addShop(shop: Shop): Boolean
    suspend fun getShoppingCart(): ShoppingCart?
    suspend fun addProductToCart(product: Product): Boolean
    suspend fun clearShoppingCart(): Boolean
    suspend fun getProductByBarcode(barcode: Long): Product?
}