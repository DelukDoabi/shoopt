package com.dedoware.shoopt.model

import com.google.gson.annotations.SerializedName
import android.os.Parcel
import android.os.Parcelable

data class Product(
    @SerializedName("id") val id: String,
    @SerializedName("barcode") val barcode: Long,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Double,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("shop") val shop: String,
    @SerializedName("picture_url") val pictureUrl: String
) : Parcelable {
    constructor() : this("", 0, 0, "", 0.0, 0.0, "", "")

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeLong(barcode)
        parcel.writeLong(timestamp)
        parcel.writeString(name)
        parcel.writeDouble(price)
        parcel.writeDouble(unitPrice)
        parcel.writeString(shop)
        parcel.writeString(pictureUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Product> {
        override fun createFromParcel(parcel: Parcel): Product {
            return Product(
                parcel.readString() ?: "",
                parcel.readLong(),
                parcel.readLong(),
                parcel.readString() ?: "",
                parcel.readDouble(),
                parcel.readDouble(),
                parcel.readString() ?: "",
                parcel.readString() ?: ""
            )
        }

        override fun newArray(size: Int): Array<Product?> {
            return arrayOfNulls(size)
        }
    }
}