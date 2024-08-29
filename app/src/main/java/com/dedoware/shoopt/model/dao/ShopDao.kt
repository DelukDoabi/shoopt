package com.dedoware.shoopt.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dedoware.shoopt.model.Shop

@Dao
interface ShopDao {

    @Query("SELECT * FROM shop")
    suspend fun getAllShops(): List<Shop>

    @Insert
    suspend fun insert(shop: Shop)
}
