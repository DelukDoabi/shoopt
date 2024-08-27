package com.dedoware.shoopt.persistence

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseImageStorage : IImageStorage {
    override suspend fun uploadImage(imageData: ByteArray, pathString: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val storageReference = Firebase.storage.reference
                val pictureRef = storageReference.child(pathString)

                pictureRef.putBytes(imageData).await()

                val downloadUrl = pictureRef.downloadUrl.await()

                Log.d("SHOOPT_TAG", "Download URL: $downloadUrl")

                downloadUrl.toString()
            } catch (e: Exception) {
                Log.e("SHOOPT_TAG", "Error uploading product picture", e)
                null
            }
        }
    }

    override suspend fun deleteImage(imageUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val storageReference = Firebase.storage.getReferenceFromUrl(imageUrl)
                storageReference.delete().await()
                true
            } catch (e: Exception) {
                Log.e("SHOOPT_TAG", "Error deleting product picture", e)
                false
            }
        }
    }
}
