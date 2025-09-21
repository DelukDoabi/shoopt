package com.dedoware.shoopt.gamification.dao

import androidx.room.*
import com.dedoware.shoopt.gamification.models.UserAchievement

@Dao
interface UserAchievementDao {

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId")
    suspend fun getUserAchievements(userId: String): List<UserAchievement>

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND achievement_id = :achievementId")
    suspend fun getUserAchievement(userId: String, achievementId: String): UserAchievement?

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND is_completed = 1")
    suspend fun getCompletedAchievements(userId: String): List<UserAchievement>

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND is_completed = 0")
    suspend fun getInProgressAchievements(userId: String): List<UserAchievement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAchievement(userAchievement: UserAchievement)

    @Update
    suspend fun updateUserAchievement(userAchievement: UserAchievement)

    @Query("DELETE FROM user_achievements WHERE user_id = :userId")
    suspend fun deleteUserAchievements(userId: String)
}
