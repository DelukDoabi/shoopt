package com.dedoware.shoopt.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey val dbRefKey: String,
    val content: String
)