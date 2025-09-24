package com.dedoware.shoopt.gamification.manager

import android.content.Context
import android.os.Bundle
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.gamification.dao.AchievementDao
import com.dedoware.shoopt.gamification.dao.UserAchievementDao
import com.dedoware.shoopt.gamification.dao.UserProfileDao
import com.dedoware.shoopt.gamification.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestionnaire principal du système de gamification
 * Gère les XP, achievements et profil utilisateur
 */
class GamificationManager(
    private val context: Context,
    private val achievementDao: AchievementDao,
    private val userAchievementDao: UserAchievementDao,
    private val userProfileDao: UserProfileDao
) {

    companion object {
        // Events pour déclencher les achievements
        const val EVENT_FIRST_PRODUCT_ADDED = "first_product_added"
        const val EVENT_PRODUCT_ADDED = "product_added"
        const val EVENT_SHOPPING_SESSION_COMPLETED = "shopping_session_completed"
        const val EVENT_BARCODE_SCANNED = "barcode_scanned"
        const val EVENT_PRICE_COMPARED = "price_compared"

        @Volatile
        private var INSTANCE: GamificationManager? = null

        fun getInstance(
            context: Context,
            achievementDao: AchievementDao,
            userAchievementDao: UserAchievementDao,
            userProfileDao: UserProfileDao
        ): GamificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamificationManager(context, achievementDao, userAchievementDao, userProfileDao)
                    .also { INSTANCE = it }
            }
        }
    }

    /**
     * Déclenche un événement et vérifie les achievements associés
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun triggerEvent(userId: String, eventType: String, metadata: Map<String, Any> = emptyMap()) {
        when (eventType) {
            EVENT_FIRST_PRODUCT_ADDED -> handleFirstProductAdded(userId)
            EVENT_PRODUCT_ADDED -> handleProductAdded(userId)
            EVENT_SHOPPING_SESSION_COMPLETED -> handleShoppingSessionCompleted(userId)
            EVENT_BARCODE_SCANNED -> handleBarcodeScanned(userId)
            EVENT_PRICE_COMPARED -> handlePriceCompared(userId)
        }

        // Log analytics
        val params = Bundle().apply {
            putString("event_type", eventType)
            putString("user_id", userId)
        }
        AnalyticsService.getInstance(ShooptApplication.instance).logEvent("gamification_event", params)
    }

    /**
     * Gère l'ajout du premier produit - déclenche l'achievement spécial
     */
    private suspend fun handleFirstProductAdded(userId: String) {
        val profile = getOrCreateUserProfile(userId)

        // Marquer le premier produit ajouté
        incrementProductsAdded(userId)

        // Déclencher l'achievement "Premier Produit"
        val firstProductAchievement = achievementDao.getAchievementById("first_product")
        if (firstProductAchievement != null) {
            completeAchievement(userId, firstProductAchievement)
        }

        // Recalculer les pourcentages XP
        updateUserXpAndLevel(userId, 100) // 100 XP pour le premier produit
    }

    /**
     * Gère l'ajout d'un produit standard
     */
    private suspend fun handleProductAdded(userId: String) {
        incrementProductsAdded(userId)
        updateUserXpAndLevel(userId, 25) // 25 XP par produit

        // Vérifier les achievements basés sur le nombre de produits
        checkProductBasedAchievements(userId)
    }

    /**
     * Complète un achievement pour l'utilisateur
     */
    private suspend fun completeAchievement(userId: String, achievement: Achievement) {
        val existingUserAchievement = userAchievementDao.getUserAchievement(userId, achievement.id)

        if (existingUserAchievement == null || !existingUserAchievement.isCompleted) {
            val userAchievement = UserAchievement(
                id = "${userId}_${achievement.id}",
                achievementId = achievement.id,
                userId = userId,
                currentProgress = achievement.requiredCount,
                isCompleted = true,
                completedAt = System.currentTimeMillis()
            )

            userAchievementDao.insertUserAchievement(userAchievement)
            incrementAchievementsCompleted(userId)

            // Ajouter l'XP de l'achievement
            updateUserXpAndLevel(userId, achievement.xpReward)

            // Déclencher la célébration (analytics)
            triggerCelebration(userId, achievement)

            // Notifier et synchroniser le manager simplifié pour mettre à jour le store local et l'UI
            try {
                val simplified = SimplifiedGamificationManager.getInstance(context)
                simplified.markAchievementCompletedLocally(userId, achievement)
            } catch (e: Exception) {
                // Si Simplified n'est pas disponible, on ignore silencieusement
                android.util.Log.w("GamificationManager", "Unable to notify simplified manager: ${e.message}")
            }
        }
    }

    /**
     * Met à jour l'XP et le niveau de l'utilisateur
     */
    private suspend fun updateUserXpAndLevel(userId: String, xpToAdd: Int) {
        val profile = getOrCreateUserProfile(userId)
        val newTotalXp = profile.totalXp + xpToAdd

        // Calculer le nouveau niveau
        val newLevel = calculateLevelFromXp(newTotalXp)
        val xpInCurrentLevel = newTotalXp - getXpRequiredForLevel(newLevel)

        val updatedProfile = profile.copy(
            totalXp = newTotalXp,
            currentLevel = newLevel,
            xpInCurrentLevel = xpInCurrentLevel,
            lastActivity = System.currentTimeMillis()
        )

        userProfileDao.updateUserProfile(updatedProfile)

        // Vérifier si level up
        if (newLevel > profile.currentLevel) {
            triggerLevelUp(userId, newLevel)
        }
    }

    /**
     * Calcule le niveau basé sur l'XP total
     */
    private fun calculateLevelFromXp(totalXp: Int): Int {
        var level = 1
        while (getXpRequiredForLevel(level + 1) <= totalXp) {
            level++
        }
        return level
    }

    /**
     * Calcule l'XP requis pour un niveau donné
     */
    private fun getXpRequiredForLevel(level: Int): Int {
        return (100 * Math.pow(level.toDouble(), 1.5)).toInt()
    }

    /**
     * Obtient ou crée le profil utilisateur
     */
    suspend fun getOrCreateUserProfile(userId: String): UserProfile {
        return userProfileDao.getUserProfile(userId) ?: run {
            val newProfile = UserProfile(userId = userId)
            userProfileDao.insertUserProfile(newProfile)
            newProfile
        }
    }

    /**
     * Vérifie les achievements basés sur le nombre de produits
     * Maintenant lancé en parallèle pour chaque achievement éligible
     */
    private suspend fun checkProductBasedAchievements(userId: String) {
        val profile = userProfileDao.getUserProfile(userId) ?: return
        val productCount = profile.productsAdded

        // Vérifier différents seuils
        val achievements = achievementDao.getAchievementsByCategory("PRODUCTS")

        val toComplete = mutableListOf<Achievement>()
        achievements.forEach { achievement ->
            if (productCount >= achievement.requiredCount) {
                val userAchievement = userAchievementDao.getUserAchievement(userId, achievement.id)
                if (userAchievement == null || !userAchievement.isCompleted) {
                    toComplete.add(achievement)
                }
            }
        }

        // Lancer les complétions en parallèle (fire-and-forget)
        toComplete.forEach { achievement ->
            CoroutineScope(Dispatchers.IO).launch {
                completeAchievement(userId, achievement)
            }
        }
    }

    /**
     * Déclenche une célébration pour un achievement
     */
    private suspend fun triggerCelebration(userId: String, achievement: Achievement) {
        withContext(Dispatchers.Main) {
            val params = Bundle().apply {
                putString("achievement_id", achievement.id)
                putString("achievement_title", achievement.title)
                putInt("xp_reward", achievement.xpReward)
                putString("user_id", userId)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("achievement_unlocked", params)
        }
    }

    /**
     * Déclenche un level up
     */
    private suspend fun triggerLevelUp(userId: String, newLevel: Int) {
        withContext(Dispatchers.Main) {
            val params = Bundle().apply {
                putInt("new_level", newLevel)
                putString("user_id", userId)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("level_up", params)
        }
    }

    /**
     * Calcule le pourcentage de progression XP global de l'utilisateur
     */
    suspend fun getUserXpProgressPercentage(userId: String): Float {
        val profile = getOrCreateUserProfile(userId)
        val totalAchievements = achievementDao.getAllActiveAchievements().size
        val completedAchievements = userAchievementDao.getCompletedAchievements(userId).size

        return if (totalAchievements > 0) {
            (completedAchievements.toFloat() / totalAchievements.toFloat()) * 100f
        } else {
            0f
        }
    }

    // Méthodes helper pour l'incrément des statistiques
    private suspend fun incrementProductsAdded(userId: String) {
        val profile = getOrCreateUserProfile(userId)
        val updatedProfile = profile.copy(
            productsAdded = profile.productsAdded + 1,
            lastActivity = System.currentTimeMillis()
        )
        userProfileDao.updateUserProfile(updatedProfile)
    }

    private suspend fun incrementAchievementsCompleted(userId: String) {
        val profile = getOrCreateUserProfile(userId)
        val updatedProfile = profile.copy(
            achievementsCompleted = profile.achievementsCompleted + 1,
            lastActivity = System.currentTimeMillis()
        )
        userProfileDao.updateUserProfile(updatedProfile)
    }

    // Gestion des autres événements
    private suspend fun handleShoppingSessionCompleted(userId: String) {
        val profile = getOrCreateUserProfile(userId)
        val updatedProfile = profile.copy(
            shoppingSessions = profile.shoppingSessions + 1,
            lastActivity = System.currentTimeMillis()
        )
        userProfileDao.updateUserProfile(updatedProfile)
        updateUserXpAndLevel(userId, 50)

        // Vérifier les achievements de la catégorie SHOPPING
        val shoppingAchievements = achievementDao.getAchievementsByCategory("SHOPPING")

        val toComplete = mutableListOf<Achievement>()
        shoppingAchievements.forEach { achievement ->
            if (updatedProfile.shoppingSessions >= achievement.requiredCount) {
                val userAchievement = userAchievementDao.getUserAchievement(userId, achievement.id)
                if (userAchievement == null || !userAchievement.isCompleted) {
                    toComplete.add(achievement)
                }
            }
        }

        toComplete.forEach { achievement ->
            CoroutineScope(Dispatchers.IO).launch {
                completeAchievement(userId, achievement)
            }
        }
    }

    private suspend fun handleBarcodeScanned(userId: String) {
        updateUserXpAndLevel(userId, 10)

        // Vérifier et compléter l'achievement du premier scan
        val barcodeAchievement = achievementDao.getAchievementById("first_barcode_scan")
        if (barcodeAchievement != null) {
            val userAchievement = userAchievementDao.getUserAchievement(userId, barcodeAchievement.id)
            if (userAchievement == null || !userAchievement.isCompleted) {
                completeAchievement(userId, barcodeAchievement)
            }
        }
    }

    private suspend fun handlePriceCompared(userId: String) {
        updateUserXpAndLevel(userId, 15)

        // Vérifier et compléter l'achievement de comparaison de prix
        val priceAchievement = achievementDao.getAchievementById("price_comparison")
        if (priceAchievement != null) {
            val userAchievement = userAchievementDao.getUserAchievement(userId, priceAchievement.id)
            if (userAchievement == null || !userAchievement.isCompleted) {
                completeAchievement(userId, priceAchievement)
            }
        }
    }
}
