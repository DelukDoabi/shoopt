package com.dedoware.shoopt.gamification.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Modèle pour le profil utilisateur avec système XP et niveaux
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    @SerializedName("user_id") val userId: String,

    @SerializedName("total_xp") val totalXp: Int = 0,
    @SerializedName("current_level") val currentLevel: Int = 1,
    @SerializedName("xp_in_current_level") val xpInCurrentLevel: Int = 0,
    @SerializedName("achievements_completed") val achievementsCompleted: Int = 0,
    @SerializedName("products_added") val productsAdded: Int = 0,
    @SerializedName("shopping_sessions") val shoppingSessions: Int = 0,
    @SerializedName("streak_days") val streakDays: Int = 0,
    @SerializedName("last_activity") val lastActivity: Long = System.currentTimeMillis(),
    @SerializedName("profile_created_at") val profileCreatedAt: Long = System.currentTimeMillis(),
    @SerializedName("favorite_achievement_category") val favoriteAchievementCategory: String? = null
) {
    constructor() : this("")

    /**
     * Calcule le pourcentage de progression vers le niveau suivant
     */
    fun getProgressPercentage(): Float {
        val xpForNextLevel = getXpRequiredForLevel(currentLevel + 1)
        val xpForCurrentLevel = getXpRequiredForLevel(currentLevel)
        val xpRange = xpForNextLevel - xpForCurrentLevel
        return if (xpRange > 0) (xpInCurrentLevel.toFloat() / xpRange) * 100f else 100f
    }

    /**
     * Calcule l'XP requis pour un niveau donné
     */
    private fun getXpRequiredForLevel(level: Int): Int {
        // Formule progressive : 100 * level^1.5
        return (100 * Math.pow(level.toDouble(), 1.5)).toInt()
    }

    /**
     * Retourne le titre du niveau actuel
     */
    fun getLevelTitle(): String {
        return when (currentLevel) {
            1 -> "Shopper Novice"
            2 -> "Price Hunter"
            3 -> "Smart Buyer"
            4 -> "Shopping Expert"
            5 -> "Deal Master"
            6 -> "Bargain Legend"
            7 -> "Shopping Guru"
            8 -> "Price Oracle"
            9 -> "Shopping Champion"
            10 -> "Ultimate Shopper"
            else -> "Shopping God (Lvl $currentLevel)"
        }
    }
}
