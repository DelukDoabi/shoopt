package com.dedoware.shoopt.gamification.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.gamification.models.Achievement
import com.dedoware.shoopt.gamification.models.UserProfile
import com.dedoware.shoopt.utils.CelebrationSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vue personnalisée pour afficher les célébrations d'achievements et le profil XP
 */
class GamificationCelebrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private lateinit var achievementIcon: ImageView
    private lateinit var achievementTitle: TextView
    private lateinit var achievementDescription: TextView
    private lateinit var xpRewardText: TextView
    private lateinit var levelText: TextView
    private lateinit var xpProgressBar: ProgressBar
    private lateinit var xpProgressText: TextView
    private lateinit var closeButton: ImageView
    private lateinit var celebrationContainer: LinearLayout

    private val celebrationSoundManager = CelebrationSoundManager.getInstance(context)

    init {
        setupView()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.view_gamification_celebration, this, true)

        // Initialiser les vues (sera créé dans le layout XML)
        achievementIcon = findViewById(R.id.achievement_icon)
        achievementTitle = findViewById(R.id.achievement_title)
        achievementDescription = findViewById(R.id.achievement_description)
        xpRewardText = findViewById(R.id.xp_reward_text)
        levelText = findViewById(R.id.level_text)
        xpProgressBar = findViewById(R.id.xp_progress_bar)
        xpProgressText = findViewById(R.id.xp_progress_text)
        closeButton = findViewById(R.id.close_button)
        celebrationContainer = findViewById(R.id.celebration_container)

        // Style de la card
        radius = 24f
        cardElevation = 16f
        setCardBackgroundColor(context.getColor(android.R.color.white))

        // Initialement invisible
        visibility = View.GONE
        alpha = 0f

        closeButton.setOnClickListener {
            hideCelebration()
        }
    }

    /**
     * Affiche une célébration pour un achievement débloqué
     */
    fun showAchievementCelebration(
        achievement: Achievement,
        userProfile: UserProfile,
        xpProgressPercentage: Float,
        onDismiss: (() -> Unit)? = null
    ) {
        // Mettre à jour le contenu
        achievementTitle.text = "🎉 ${achievement.title}"
        achievementDescription.text = achievement.description
        xpRewardText.text = "+${achievement.xpReward} XP"
        levelText.text = "Niveau ${userProfile.currentLevel} - ${userProfile.getLevelTitle()}"

        // Mettre à jour la barre de progression XP
        xpProgressBar.progress = xpProgressPercentage.toInt()
        xpProgressText.text = "${xpProgressPercentage.toInt()}% XP"

        // Définir l'icône (pour l'instant un emoji par défaut)
        setAchievementIcon(achievement.icon)

        // Afficher avec animation
        showWithAnimation()

        // Jouer le son de célébration
        celebrationSoundManager.playCelebrationSound()

        // Masquer automatiquement après 5 secondes
        CoroutineScope(Dispatchers.Main).launch {
            delay(5000)
            if (visibility == View.VISIBLE) {
                hideCelebration(onDismiss)
            }
        }
    }

    /**
     * Affiche une célébration pour le premier produit ajouté
     */
    fun showFirstProductCelebration(
        userProfile: UserProfile,
        xpProgressPercentage: Float,
        onDismiss: (() -> Unit)? = null
    ) {
        achievementTitle.text = "🎉 Félicitations !"
        achievementDescription.text = "Vous avez ajouté votre premier produit ! Continuez à explorer pour débloquer plus d'achievements."
        xpRewardText.text = "+100 XP"
        levelText.text = "Niveau ${userProfile.currentLevel} - ${userProfile.getLevelTitle()}"

        xpProgressBar.progress = xpProgressPercentage.toInt()
        xpProgressText.text = "${xpProgressPercentage.toInt()}% XP global"

        achievementIcon.setImageResource(R.drawable.ic_celebration) // Fallback icon

        showWithAnimation()
        celebrationSoundManager.playCelebrationSound()

        CoroutineScope(Dispatchers.Main).launch {
            delay(6000) // Un peu plus long pour le premier produit
            if (visibility == View.VISIBLE) {
                hideCelebration(onDismiss)
            }
        }
    }

    private fun setAchievementIcon(iconName: String) {
        // Mapping des icônes - remplacé par des icônes disponibles dans le projet
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

    private fun showWithAnimation() {
        visibility = View.VISIBLE

        // Animation d'apparition avec bounce
        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 0.7f, 1.05f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 0.7f, 1.05f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 600
        animatorSet.interpolator = AccelerateDecelerateInterpolator()

        animatorSet.start()

        // Animation de la barre XP
        val progressAnimator = ObjectAnimator.ofInt(xpProgressBar, "progress", 0, xpProgressBar.progress)
        progressAnimator.duration = 1000
        progressAnimator.startDelay = 300
        progressAnimator.start()
    }

    private fun hideCelebration(onDismiss: (() -> Unit)? = null) {
        val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)
        val scaleDown = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.9f)
        val scaleDownY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 0.9f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeOut, scaleDown, scaleDownY)
        animatorSet.duration = 300

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                visibility = View.GONE
                onDismiss?.invoke()
            }
        })

        animatorSet.start()
    }
}
