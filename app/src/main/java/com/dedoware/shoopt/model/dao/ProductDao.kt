package com.dedoware.shoopt.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dedoware.shoopt.model.Product

@Dao
interface ProductDao {
    @Insert
    suspend fun insert(product: Product)

    @Update
    suspend fun update(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): Product?

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: Long): Product?
}
