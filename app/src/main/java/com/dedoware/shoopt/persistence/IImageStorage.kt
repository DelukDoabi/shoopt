package com.dedoware.shoopt.persistence

interface IImageStorage {
    suspend fun uploadImage(imageData: ByteArray, pathString: String): String?
    suspend fun deleteImage(imageUrl: String): Boolean
}
