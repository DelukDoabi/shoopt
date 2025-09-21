package com.dedoware.shoopt.gamification.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.dedoware.shoopt.gamification.models.UserProfile
import com.dedoware.shoopt.gamification.data.DefaultAchievements
import com.dedoware.shoopt.gamification.models.Achievement
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

    // Nouvelle interface pour les callbacks d'achievements
    interface AchievementUnlockedListener {
        suspend fun onAchievementUnlocked(userId: String, achievement: Achievement)
    }

    // Liste des listeners pour les événements d'achievement
    private val achievementListeners = mutableListOf<AchievementUnlockedListener>()

    companion object {
        const val EVENT_FIRST_PRODUCT_ADDED = "first_product_added"
        const val EVENT_PRODUCT_ADDED = "product_added"
        const val EVENT_BARCODE_SCANNED = "barcode_scanned"
        const val EVENT_PRICE_COMPARED = "price_compared"

        @Volatile
        private var INSTANCE: SimplifiedGamificationManager? = null

        fun getInstance(context: Context): SimplifiedGamificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimplifiedGamificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Ajoute un listener pour les événements d'achievement débloqué
     */
    fun addAchievementUnlockedListener(listener: AchievementUnlockedListener) {
        if (!achievementListeners.contains(listener)) {
            achievementListeners.add(listener)
        }
    }

    /**
     * Retire un listener pour les événements d'achievement débloqué
     */
    fun removeAchievementUnlockedListener(listener: AchievementUnlockedListener) {
        achievementListeners.remove(listener)
    }

    /**
     * Déclenche un événement gamification
     */
    suspend fun triggerEvent(userId: String, eventType: String) {
        when (eventType) {
            EVENT_FIRST_PRODUCT_ADDED -> handleFirstProductAdded(userId)
            EVENT_PRODUCT_ADDED -> handleProductAdded(userId)
            EVENT_BARCODE_SCANNED -> handleBarcodeScanned(userId)
            EVENT_PRICE_COMPARED -> handlePriceCompared(userId)
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
     * Gère un scan de code-barres (débloque "first_barcode_scan")
     */
    private suspend fun handleBarcodeScanned(userId: String) {
        val profile = getOrCreateUserProfile(userId)

        // Ajouter l'XP pour le scan
        val updatedProfile = profile.copy(
            totalXp = profile.totalXp + 10,
            lastActivity = System.currentTimeMillis()
        )
        saveUserProfile(userId, updatedProfile)

        // Marquer l'achievement "first_barcode_scan" si non complété
        if (!isAchievementCompleted(userId, "first_barcode_scan")) {
            markAchievementCompleted(userId, "first_barcode_scan")

            // Notifier les listeners avec l'objet achievement depuis la configuration par défaut
            val achievement = DefaultAchievements.getDefaultAchievements().find { it.id == "first_barcode_scan" }
            if (achievement != null) {
                notifyAchievementUnlocked(userId, achievement)
            }
        }
    }

    /**
     * Gère une comparaison de prix (débloque "price_comparison")
     */
    private suspend fun handlePriceCompared(userId: String) {
        val profile = getOrCreateUserProfile(userId)

        // Ajouter l'XP pour la comparaison
        val updatedProfile = profile.copy(
            totalXp = profile.totalXp + 15,
            lastActivity = System.currentTimeMillis()
        )
        saveUserProfile(userId, updatedProfile)

        if (!isAchievementCompleted(userId, "price_comparison")) {
            markAchievementCompleted(userId, "price_comparison")

            val achievement = DefaultAchievements.getDefaultAchievements().find { it.id == "price_comparison" }
            if (achievement != null) {
                notifyAchievementUnlocked(userId, achievement)
            }
        }
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

                // Notifier les listeners de l'achievement débloqué
                notifyAchievementUnlocked(userId, achievement)
            }
        }
    }

    /**
     * Notifie les listeners qu'un achievement a été débloqué
     */
    private suspend fun notifyAchievementUnlocked(userId: String, achievement: Achievement) {
        for (listener in achievementListeners) {
            listener.onAchievementUnlocked(userId, achievement)
        }
    }

    /**
     * Méthode publique pour poster un achievement débloqué depuis d'autres gestionnaires
     * Permet à `GamificationManager` (ou autre) d'annoncer qu'un achievement a été complété
     * et déclencher ainsi les listeners enregistrés (ex: affichage de célébration).
     */
    suspend fun postAchievementUnlocked(userId: String, achievement: Achievement) {
        // Simple délégation vers la méthode interne de notification
        notifyAchievementUnlocked(userId, achievement)
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
                } catch (_: Exception) {
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
            } catch (_: Exception) {
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

    /**
     * Synchronise le nombre de produits dans le profil utilisateur avec le nombre réel
     * de produits en base de données
     */
    suspend fun synchronizeProductCount(userId: String, actualProductCount: Int) {
        val profile = getOrCreateUserProfile(userId)

        // Ne pas réduire le nombre de produits si le compteur actuel est plus grand
        // Cela évite de reprendre des récompenses déjà accordées en cas de suppression de produits
        if (actualProductCount > profile.productsAdded) {
            val oldCount = profile.productsAdded
            val updatedProfile = profile.copy(
                productsAdded = actualProductCount,
                lastActivity = System.currentTimeMillis()
            )

            saveUserProfile(userId, updatedProfile)

            // Vérifier si de nouveaux achievements sont débloqués suite à cette synchronisation
            // (oldCount < actualProductCount est vrai ici)
            checkProductAchievements(userId, actualProductCount)

            // Log de debug pour suivre la synchronisation
            android.util.Log.d("SHOOPT_GAMIFICATION",
                "Synchronisation des produits: $oldCount -> $actualProductCount produits")
        }
    }

    /**
     * Méthode publique pour marquer un achievement comme complété dans le store local
     * et notifier les listeners. Utile lorsque le gestionnaire central (avec DB) complète
     * un achievement et doit synchroniser l'état local utilisé par l'UI.
     */
    suspend fun markAchievementCompletedLocally(userId: String, achievement: Achievement) {
        // Log pour faciliter le debug
        android.util.Log.d("SHOOPT_GAMIFICATION", "Marking achievement locally: ${achievement.id} for user $userId")

        // Marquer dans le store local
        markAchievementCompleted(userId, achievement.id)

        // Mettre à jour le profil local si nécessaire (ajouter XP si défini)
        try {
            val profile = getOrCreateUserProfile(userId)
            val updatedProfile = profile.copy(
                totalXp = profile.totalXp + achievement.xpReward,
                achievementsCompleted = profile.achievementsCompleted + 1,
                lastActivity = System.currentTimeMillis()
            )
            saveUserProfile(userId, updatedProfile)
        } catch (_: Exception) {
            // Ne pas bloquer si la mise à jour du profil local échoue
        }

        // Notifier les listeners
        notifyAchievementUnlocked(userId, achievement)
    }
}
