package com.dedoware.shoopt.persistence

import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.Shop

interface IProductRepository {
    suspend fun getUniqueId(): String?
    suspend fun getAll(): List<Product>
    suspend fun getById(id: String): Product?
    suspend fun insert(product: Product): String
    suspend fun update(product: Product): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun getShops(): List<String>
    suspend fun addShop(shop: Shop): Boolean
    suspend fun deleteProduct(product: Product): Boolean
}