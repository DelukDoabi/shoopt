package com.dedoware.shoopt.persistence

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LocalImageStorage(private val context: Context) : IImageStorage {

    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override suspend fun uploadImage(imageData: ByteArray, pathString: String): String? {
        return try {
            // Create the file object with the full path
            val file = File(imagesDir, pathString)

            // Ensure the parent directories exist
            file.parentFile?.apply {
                if (!exists()) {
                    mkdirs()  // Create any missing directories
                }
            }

            // Write the file
            FileOutputStream(file).use { fos ->
                fos.write(imageData)
                file.absolutePath
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deleteImage(imageUrl: String): Boolean {
        return try {
            val file = File(imageUrl)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
