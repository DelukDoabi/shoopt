package com.dedoware.shoopt.model

import com.google.gson.annotations.SerializedName

data class Product(
    @SerializedName("id") val id: String,
    @SerializedName("barcode") val barcode: Long,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Double,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("shop") val shop: String,
    @SerializedName("picture_url") val pictureUrl: String
) {
    constructor() : this("", 0, "", 0.0, 0.0, "", "")
}