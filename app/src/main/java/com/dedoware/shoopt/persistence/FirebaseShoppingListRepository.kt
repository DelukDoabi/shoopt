package com.dedoware.shoopt.persistence

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseShoppingListRepository : IShoppingListRepository {
    private val database = FirebaseDatabase.getInstance().reference

    override suspend fun getShoppingList(dbRefKey: String): String? = withContext(Dispatchers.IO) {
        val snapshot = database.child(dbRefKey).get().await()
        snapshot.getValue(String::class.java)
    }

    override suspend fun saveShoppingList(dbRefKey: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            database.child(dbRefKey).setValue(content).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
