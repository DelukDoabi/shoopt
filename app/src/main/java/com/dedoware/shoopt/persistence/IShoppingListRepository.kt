package com.dedoware.shoopt.persistence

interface IShoppingListRepository {
    suspend fun getShoppingList(dbRefKey: String): String?
    suspend fun saveShoppingList(dbRefKey: String, content: String): Boolean
}
