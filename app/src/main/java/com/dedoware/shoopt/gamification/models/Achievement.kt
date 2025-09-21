package com.dedoware.shoopt.gamification.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.annotations.SerializedName

/**
 * Modèle pour représenter un achievement/défi dans le système de gamification
 */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    @SerializedName("id") val id: String,

    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String, // Nom de l'icône ou URL
    @SerializedName("category") val category: String, // Stocké comme String pour Room
    @SerializedName("xp_reward") val xpReward: Int,
    @SerializedName("required_count") val requiredCount: Int = 1, // Pour les achievements avec compteur
    @SerializedName("is_repeatable") val isRepeatable: Boolean = false,
    @SerializedName("unlock_condition") val unlockCondition: String? = null, // Condition pour débloquer
    @SerializedName("difficulty") val difficulty: String = "EASY", // Stocké comme String pour Room
    @SerializedName("is_active") val isActive: Boolean = true
) {
    constructor() : this("", "", "", "", "GENERAL", 0)

    // Helper methods pour convertir les enums
    fun getCategoryEnum(): AchievementCategory {
        return try {
            AchievementCategory.valueOf(category)
        } catch (e: Exception) {
            AchievementCategory.GENERAL
        }
    }

    fun getDifficultyEnum(): AchievementDifficulty {
        return try {
            AchievementDifficulty.valueOf(difficulty)
        } catch (e: Exception) {
            AchievementDifficulty.EASY
        }
    }
}

enum class AchievementCategory {
    GENERAL,      // Achievements généraux
    PRODUCTS,     // Liés aux produits
    SHOPPING,     // Liés au shopping/courses
    SOCIAL,       // Partage, etc.
    EXPLORATION,  // Découverte de nouvelles fonctionnalités
    CONSISTENCY   // Utilisation régulière
}

enum class AchievementDifficulty {
    EASY,     // 50-100 XP
    MEDIUM,   // 150-300 XP
    HARD,     // 400-600 XP
    EPIC      // 700+ XP
}
