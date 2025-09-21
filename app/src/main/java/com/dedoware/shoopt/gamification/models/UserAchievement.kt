package com.dedoware.shoopt.gamification.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Modèle pour suivre les progrès de l'utilisateur sur les achievements
 */
@Entity(tableName = "user_achievements")
data class UserAchievement(
    @PrimaryKey
    @SerializedName("id") val id: String,

    @SerializedName("achievement_id") val achievementId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("current_progress") val currentProgress: Int = 0,
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("completed_at") val completedAt: Long? = null,
    @SerializedName("last_updated") val lastUpdated: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "")
}
