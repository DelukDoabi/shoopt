package com.dedoware.shoopt.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour stocker les taux de change en cache
 */
@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey
    val baseCurrency: String,
    val ratesJson: String,
    val timestamp: Long
)
