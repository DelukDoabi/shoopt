package com.dedoware.shoopt.persistence

import com.dedoware.shoopt.model.ShoppingList
import com.dedoware.shoopt.model.dao.ShoppingListDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalShoppingListRepository(private val shoppingListDao: ShoppingListDao) : IShoppingListRepository {

    override suspend fun getShoppingList(dbRefKey: String): String? = withContext(Dispatchers.IO) {
        shoppingListDao.getShoppingList(dbRefKey)
    }

    override suspend fun saveShoppingList(dbRefKey: String, content: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            shoppingListDao.saveShoppingList(ShoppingList(dbRefKey, content))
            true
        } catch (e: Exception) {
            false
        }
    }
}
