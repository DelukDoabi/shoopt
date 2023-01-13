package com.dedoware.shoopt.model

import com.google.gson.annotations.SerializedName

class Product(
    @SerializedName("barcode") val barcode: Number,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Number,
    @SerializedName("unit_price") val unitPrice: Number,
    @SerializedName("shop") val shop: String
)