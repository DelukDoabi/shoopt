package com.dedoware.shoopt.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dedoware.shoopt.model.ExchangeRateEntity

/**
 * DAO pour accéder aux taux de change stockés en cache
 */
@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates WHERE baseCurrency = :baseCurrency")
    suspend fun getExchangeRate(baseCurrency: String): ExchangeRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exchangeRate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAll()
}
