package com.dedoware.shoopt.gamification.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.gamification.manager.SimplifiedGamificationManager
import com.dedoware.shoopt.gamification.models.UserProfile
import com.dedoware.shoopt.gamification.models.Achievement
import com.dedoware.shoopt.gamification.models.UserAchievement
import com.dedoware.shoopt.gamification.data.DefaultAchievements
import kotlinx.coroutines.launch

/**
 * Fragment moderne pour afficher le profil utilisateur avec le système de gamification
 */
class UserProfileFragment : Fragment() {

    private lateinit var gamificationManager: SimplifiedGamificationManager
    private lateinit var profileHeaderView: View
    private lateinit var achievementsRecyclerView: RecyclerView
    private lateinit var achievementsAdapter: AchievementsAdapter

    // Views du header de profil
    private lateinit var userLevelText: TextView
    private lateinit var userLevelTitle: TextView
    private lateinit var totalXpText: TextView
    private lateinit var levelProgressBar: ProgressBar
    private lateinit var levelProgressText: TextView
    private lateinit var achievementsCompletedText: TextView
    private lateinit var productsAddedText: TextView
    private lateinit var shoppingSessionsText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialiser le manager de gamification
        gamificationManager = SimplifiedGamificationManager.getInstance(requireContext())

        initializeViews(view)
        setupRecyclerView()
        loadUserProfile()
    }

    private fun initializeViews(view: View) {
        profileHeaderView = view.findViewById(R.id.profile_header)
        achievementsRecyclerView = view.findViewById(R.id.achievements_recycler_view)

        // Header views
        userLevelText = view.findViewById(R.id.user_level_text)
        userLevelTitle = view.findViewById(R.id.user_level_title)
        totalXpText = view.findViewById(R.id.total_xp_text)
        levelProgressBar = view.findViewById(R.id.level_progress_bar)
        levelProgressText = view.findViewById(R.id.level_progress_text)
        achievementsCompletedText = view.findViewById(R.id.achievements_completed_text)
        productsAddedText = view.findViewById(R.id.products_added_text)
        shoppingSessionsText = view.findViewById(R.id.shopping_sessions_text)
    }

    private fun setupRecyclerView() {
        achievementsAdapter = AchievementsAdapter { achievement ->
            // Action lors du clic sur un achievement
            showAchievementDetails(achievement)
        }

        achievementsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        achievementsRecyclerView.adapter = achievementsAdapter
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val userId = getCurrentUserId()
                val userProfile = gamificationManager.getOrCreateUserProfile(userId)
                val xpProgressPercentage = gamificationManager.getUserXpProgressPercentage(userId)

                updateProfileHeader(userProfile, xpProgressPercentage)
                loadAchievements(userId)

            } catch (e: Exception) {
                showError("Erreur lors du chargement du profil: ${e.message}")
            }
        }
    }

    private fun updateProfileHeader(userProfile: UserProfile, xpProgressPercentage: Float) {
        userLevelText.text = userProfile.currentLevel.toString()
        userLevelTitle.text = userProfile.getLevelTitle()
        totalXpText.text = "${userProfile.totalXp} XP"
        achievementsCompletedText.text = userProfile.achievementsCompleted.toString()
        productsAddedText.text = userProfile.productsAdded.toString()
        shoppingSessionsText.text = userProfile.shoppingSessions.toString()

        // Mettre à jour la barre de progression XP
        levelProgressBar.progress = xpProgressPercentage.toInt()
        levelProgressText.text = "${xpProgressPercentage.toInt()}%"
    }

    private fun loadAchievements(userId: String) {
        lifecycleScope.launch {
            try {
                // Charger tous les achievements disponibles
                val allAchievements = DefaultAchievements.getDefaultAchievements()

                // Créer des items d'affichage pour les achievements
                val achievementItems = allAchievements.map { achievement ->
                    val isCompleted = gamificationManager.isAchievementCompleted(userId, achievement.id)
                    AchievementDisplayItem(
                        achievement = achievement,
                        userAchievement = null, // Simplified version doesn't track individual progress
                        isCompleted = isCompleted,
                        progress = if (isCompleted) achievement.requiredCount else 0
                    )
                }

                achievementsAdapter.updateAchievements(achievementItems)
            } catch (e: Exception) {
                showError("Erreur lors du chargement des achievements: ${e.message}")
            }
        }
    }

    private fun showAchievementDetails(achievement: Achievement) {
        // Afficher les détails de l'achievement dans un dialog
        // Implementation à venir - pour l'instant on fait un toast simple
        Toast.makeText(requireContext(), "Achievement: ${achievement.title}", Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentUserId(): String {
        // Retourner l'ID utilisateur actuel - à adapter selon votre système d'auth
        return "current_user_id" // Placeholder
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Data class pour afficher un achievement avec son statut
 */
data class AchievementDisplayItem(
    val achievement: Achievement,
    val userAchievement: UserAchievement?,
    val isCompleted: Boolean,
    val progress: Int
)

/**
 * Adapter pour afficher la liste des achievements
 */
class AchievementsAdapter(
    private val onAchievementClick: (Achievement) -> Unit
) : RecyclerView.Adapter<AchievementsAdapter.AchievementViewHolder>() {

    private var achievements = listOf<AchievementDisplayItem>()

    fun updateAchievements(newAchievements: List<AchievementDisplayItem>) {
        achievements = newAchievements
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(achievements[position])
    }

    override fun getItemCount() = achievements.size

    inner class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val achievementIcon: ImageView = itemView.findViewById(R.id.achievement_icon)
        private val achievementTitle: TextView = itemView.findViewById(R.id.achievement_title)
        private val achievementDescription: TextView = itemView.findViewById(R.id.achievement_description)
        private val achievementXp: TextView = itemView.findViewById(R.id.achievement_xp)
        private val achievementProgress: ProgressBar = itemView.findViewById(R.id.achievement_progress)
        private val achievementProgressText: TextView = itemView.findViewById(R.id.achievement_progress_text)
        private val completedBadge: ImageView = itemView.findViewById(R.id.completed_badge)

        fun bind(item: AchievementDisplayItem) {
            val achievement = item.achievement

            achievementTitle.text = achievement.title
            achievementDescription.text = achievement.description
            achievementXp.text = "${achievement.xpReward} XP"

            // Gérer l'état completed/in progress
            if (item.isCompleted) {
                completedBadge.visibility = View.VISIBLE
                achievementProgress.visibility = View.GONE
                achievementProgressText.visibility = View.GONE
                itemView.alpha = 1.0f
            } else {
                completedBadge.visibility = View.GONE

                if (achievement.requiredCount > 1) {
                    achievementProgress.visibility = View.VISIBLE
                    achievementProgressText.visibility = View.VISIBLE

                    val progressPercentage = (item.progress.toFloat() / achievement.requiredCount) * 100
                    achievementProgress.progress = progressPercentage.toInt()
                    achievementProgressText.text = "${item.progress}/${achievement.requiredCount}"
                } else {
                    achievementProgress.visibility = View.GONE
                    achievementProgressText.visibility = View.GONE
                }

                itemView.alpha = 0.7f
            }

            // Définir l'icône
            setAchievementIcon(achievement.icon)

            itemView.setOnClickListener {
                onAchievementClick(achievement)
            }
        }

        private fun setAchievementIcon(iconName: String) {
            val iconRes = when (iconName) {
                "ic_first_product" -> R.drawable.ic_add_circle
                "ic_collection" -> R.drawable.ic_product
                "ic_price_hunter" -> R.drawable.ic_price
                "ic_expert" -> R.drawable.ic_star
                "ic_master" -> R.drawable.ic_trophy_gold
                "ic_legend" -> R.drawable.ic_stars
                "ic_shopping_cart" -> R.drawable.ic_shopping_cart_confident
                "ic_barcode" -> R.drawable.ic_barcode_scan
                "ic_compare" -> R.drawable.ic_analytics
                "ic_calendar" -> R.drawable.ic_trending_up
                "ic_share" -> R.drawable.ic_person
                else -> R.drawable.ic_celebration
            }

            achievementIcon.setImageResource(iconRes)
        }
    }
}
