package com.dedoware.shoopt.gamification.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.dedoware.shoopt.gamification.models.UserProfile
import com.dedoware.shoopt.gamification.data.DefaultAchievements
import com.dedoware.shoopt.utils.AnalyticsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Version simplifiée du GamificationManager utilisant SharedPreferences
 * Pour permettre à l'application de compiler et tester le système
 */
class SimplifiedGamificationManager private constructor(context: Context) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("gamification_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val EVENT_FIRST_PRODUCT_ADDED = "first_product_added"
        const val EVENT_PRODUCT_ADDED = "product_added"

        @Volatile
        private var INSTANCE: SimplifiedGamificationManager? = null

        fun getInstance(context: Context): SimplifiedGamificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimplifiedGamificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Déclenche un événement gamification
     */
    suspend fun triggerEvent(userId: String, eventType: String) {
        when (eventType) {
            EVENT_FIRST_PRODUCT_ADDED -> handleFirstProductAdded(userId)
            EVENT_PRODUCT_ADDED -> handleProductAdded(userId)
        }

        val params = Bundle().apply {
            putString("event_type", eventType)
            putString("user_id", userId)
        }
        AnalyticsManager.logCustomEvent("gamification_event", params)
    }

    /**
     * Gère l'ajout du premier produit
     */
    private suspend fun handleFirstProductAdded(userId: String) {
        val profile = getOrCreateUserProfile(userId)

        // Marquer le premier produit et ajouter l'XP
        val updatedProfile = profile.copy(
            productsAdded = 1,
            totalXp = profile.totalXp + 100,
            achievementsCompleted = profile.achievementsCompleted + 1,
            lastActivity = System.currentTimeMillis()
        )

        saveUserProfile(userId, updatedProfile)

        // Marquer l'achievement du premier produit comme complété
        markAchievementCompleted(userId, "first_product")
    }

    /**
     * Gère l'ajout d'un produit standard
     */
    private suspend fun handleProductAdded(userId: String) {
        val profile = getOrCreateUserProfile(userId)

        val updatedProfile = profile.copy(
            productsAdded = profile.productsAdded + 1,
            totalXp = profile.totalXp + 25,
            lastActivity = System.currentTimeMillis()
        )

        saveUserProfile(userId, updatedProfile)

        // Vérifier les nouveaux achievements
        checkProductAchievements(userId, updatedProfile.productsAdded)
    }

    /**
     * Vérifie les achievements basés sur le nombre de produits
     */
    private suspend fun checkProductAchievements(userId: String, productCount: Int) {
        val achievements = DefaultAchievements.getDefaultAchievements()
            .filter { it.category == "PRODUCTS" }

        achievements.forEach { achievement ->
            if (productCount >= achievement.requiredCount && !isAchievementCompleted(userId, achievement.id)) {
                markAchievementCompleted(userId, achievement.id)

                // Ajouter l'XP de l'achievement
                val profile = getOrCreateUserProfile(userId)
                val updatedProfile = profile.copy(
                    totalXp = profile.totalXp + achievement.xpReward,
                    achievementsCompleted = profile.achievementsCompleted + 1
                )
                saveUserProfile(userId, updatedProfile)
            }
        }
    }

    /**
     * Obtient ou crée le profil utilisateur (méthode publique)
     */
    suspend fun getOrCreateUserProfile(userId: String): UserProfile {
        return withContext(Dispatchers.IO) {
            val profileJson = sharedPrefs.getString("user_profile_$userId", null)
            if (profileJson != null) {
                try {
                    gson.fromJson(profileJson, UserProfile::class.java)
                } catch (e: Exception) {
                    UserProfile(userId = userId)
                }
            } else {
                UserProfile(userId = userId)
            }
        }
    }

    /**
     * Sauvegarde le profil utilisateur
     */
    private suspend fun saveUserProfile(userId: String, profile: UserProfile) {
        withContext(Dispatchers.IO) {
            val profileJson = gson.toJson(profile)
            sharedPrefs.edit().putString("user_profile_$userId", profileJson).apply()
        }
    }

    /**
     * Marque un achievement comme complété
     */
    private suspend fun markAchievementCompleted(userId: String, achievementId: String) {
        withContext(Dispatchers.IO) {
            val completedAchievements = getCompletedAchievements(userId).toMutableSet()
            completedAchievements.add(achievementId)

            val achievementsJson = gson.toJson(completedAchievements)
            sharedPrefs.edit().putString("completed_achievements_$userId", achievementsJson).apply()
        }
    }

    /**
     * Vérifie si un achievement est complété
     */
    suspend fun isAchievementCompleted(userId: String, achievementId: String): Boolean {
        return withContext(Dispatchers.IO) {
            getCompletedAchievements(userId).contains(achievementId)
        }
    }

    /**
     * Obtient la liste des achievements complétés
     */
    private fun getCompletedAchievements(userId: String): Set<String> {
        val achievementsJson = sharedPrefs.getString("completed_achievements_$userId", null)
        return if (achievementsJson != null) {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson(achievementsJson, type) ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }
    }

    /**
     * Calcule le pourcentage de progression XP global
     */
    suspend fun getUserXpProgressPercentage(userId: String): Float {
        return withContext(Dispatchers.IO) {
            val totalAchievements = DefaultAchievements.getDefaultAchievements().filter { it.isActive }.size
            val completedAchievements = getCompletedAchievements(userId).size

            if (totalAchievements > 0) {
                (completedAchievements.toFloat() / totalAchievements.toFloat()) * 100f
            } else {
                0f
            }
        }
    }
}
