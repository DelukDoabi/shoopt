package com.dedoware.shoopt.gamification.dao

import androidx.room.*
import com.dedoware.shoopt.gamification.models.UserProfile

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE user_id = :userId")
    suspend fun getUserProfile(userId: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(userProfile: UserProfile)

    @Update
    suspend fun updateUserProfile(userProfile: UserProfile)

    @Query("UPDATE user_profile SET total_xp = total_xp + :xp WHERE user_id = :userId")
    suspend fun addXp(userId: String, xp: Int)

    @Query("UPDATE user_profile SET products_added = products_added + 1 WHERE user_id = :userId")
    suspend fun incrementProductsAdded(userId: String)

    @Query("UPDATE user_profile SET shopping_sessions = shopping_sessions + 1 WHERE user_id = :userId")
    suspend fun incrementShoppingSessions(userId: String)

    @Query("UPDATE user_profile SET achievements_completed = achievements_completed + 1 WHERE user_id = :userId")
    suspend fun incrementAchievementsCompleted(userId: String)

    @Query("DELETE FROM user_profile WHERE user_id = :userId")
    suspend fun deleteUserProfile(userId: String)
}
