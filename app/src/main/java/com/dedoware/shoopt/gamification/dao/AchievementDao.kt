package com.dedoware.shoopt.gamification.dao

import androidx.room.*
import com.dedoware.shoopt.gamification.models.Achievement

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE is_active = 1")
    suspend fun getAllActiveAchievements(): List<Achievement>

    @Query("SELECT * FROM achievements WHERE category = :category AND is_active = 1")
    suspend fun getAchievementsByCategory(category: String): List<Achievement>

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getAchievementById(id: String): Achievement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: Achievement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<Achievement>)

    @Update
    suspend fun updateAchievement(achievement: Achievement)

    @Delete
    suspend fun deleteAchievement(achievement: Achievement)
}
