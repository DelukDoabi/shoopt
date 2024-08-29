package com.dedoware.shoopt.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "shop")
data class Shop(
    @PrimaryKey @SerializedName("name") val name: String
)
