package com.dedoware.shoopt.ui.dialogs

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.dedoware.shoopt.R
import com.dedoware.shoopt.gamification.models.Achievement
import com.dedoware.shoopt.ui.effects.CelebrationParticleView
import com.dedoware.shoopt.utils.CelebrationSoundManager
import com.dedoware.shoopt.utils.FirstProductManager
import com.google.android.material.button.MaterialButton
import com.shoopt.app.utils.ShineEffectView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog moderne de félicitation pour les achievements débloqués
 * Inclut des animations fluides, effets sonores, vibrations et design moderne
 * Version généralisée à partir de FirstProductCongratulationDialog
 */
class AchievementCongratulationDialog(
    context: Context,
    private val achievement: Achievement,
    private val onDismissCallback: (() -> Unit)? = null
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private lateinit var congratsCard: CardView
    private lateinit var achievementIcon: ImageView
    private lateinit var shineEffect: ShineEffectView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var messageText: TextView
    private lateinit var rewardText: TextView
    private lateinit var continueButton: MaterialButton
    private lateinit var celebrationParticles: CelebrationParticleView

    // Pour les effets de célébration
    private val firstProductManager = FirstProductManager.getInstance(context)
    private val soundManager = CelebrationSoundManager.getInstance(context)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuration de la fenêtre
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        setupView()
        setupAnimations()
        setupClickListeners()
        startCelebrationSequence()
    }

    private fun setupView() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_first_product_congratulation, null)
        setContentView(view)

        congratsCard = view.findViewById(R.id.congratsCard)
        achievementIcon = view.findViewById(R.id.trophyIcon)
        shineEffect = view.findViewById(R.id.shineEffect)
        titleText = view.findViewById(R.id.titleText)
        subtitleText = view.findViewById(R.id.subtitleText)
        messageText = view.findViewById(R.id.messageText)
        rewardText = view.findViewById(R.id.statsText)
        continueButton = view.findViewById(R.id.continueButton)
        celebrationParticles = view.findViewById(R.id.celebrationParticles)

        // Configuration du contenu avec les données de l'achievement
        titleText.text = context.getString(R.string.achievement_unlocked)
        subtitleText.text = achievement.title
        messageText.text = achievement.description
        rewardText.text = context.getString(R.string.achievement_reward, achievement.xpReward)
        continueButton.text = context.getString(R.string.continue_shopping)

        // Icône de l'achievement
        setAchievementIcon(achievement.icon)
    }

    private fun startCelebrationSequence() {
        CoroutineScope(Dispatchers.Main).launch {
            // Démarrer immédiatement les effets multisensoriels
            firstProductManager.triggerCelebrationHaptics()

            // Démarrer les particules après un court délai
            delay(300)
            celebrationParticles.startCelebration()

            // Jouer le son de célébration
            if (soundManager.isSoundEnabled()) {
                delay(200)
                soundManager.playCelebrationSound()
            }
        }
    }

    private fun setupAnimations() {
        // Animation d'entrée de la carte
        congratsCard.scaleX = 0f
        congratsCard.scaleY = 0f
        congratsCard.alpha = 0f

        // Animation de l'icône
        achievementIcon.scaleX = 0f
        achievementIcon.scaleY = 0f
        achievementIcon.rotation = -180f

        // Animation des textes
        titleText.alpha = 0f
        titleText.translationY = 50f
        subtitleText.alpha = 0f
        subtitleText.translationY = 30f
        messageText.alpha = 0f
        messageText.translationY = 50f
        rewardText.alpha = 0f
        rewardText.translationY = 30f
        continueButton.alpha = 0f
        continueButton.translationY = 50f

        // Séquence d'animations
        startEntranceAnimations()
    }

    private fun startEntranceAnimations() {
        // Animation de la carte principale avec effet élastique
        val cardScaleX = ObjectAnimator.ofFloat(congratsCard, "scaleX", 0f, 1.1f, 1f)
        val cardScaleY = ObjectAnimator.ofFloat(congratsCard, "scaleY", 0f, 1.1f, 1f)
        val cardAlpha = ObjectAnimator.ofFloat(congratsCard, "alpha", 0f, 1f)

        val cardAnimatorSet = AnimatorSet().apply {
            playTogether(cardScaleX, cardScaleY, cardAlpha)
            duration = 800
            interpolator = OvershootInterpolator(1.5f)
        }

        // Animation de l'icône avec rotation et rebond
        val iconScaleX = ObjectAnimator.ofFloat(achievementIcon, "scaleX", 0f, 1.3f, 1f)
        val iconScaleY = ObjectAnimator.ofFloat(achievementIcon, "scaleY", 0f, 1.3f, 1f)
        val iconRotation = ObjectAnimator.ofFloat(achievementIcon, "rotation", -180f, 0f)

        val iconAnimatorSet = AnimatorSet().apply {
            playTogether(iconScaleX, iconScaleY, iconRotation)
            duration = 1000
            startDelay = 400
            interpolator = BounceInterpolator()
        }

        // Animations des textes en cascade
        val titleAnimation = createTextAnimation(titleText, 700)
        val subtitleAnimation = createTextAnimation(subtitleText, 850)
        val messageAnimation = createTextAnimation(messageText, 1000)
        val rewardAnimation = createTextAnimation(rewardText, 1150)
        val buttonAnimation = createTextAnimation(continueButton, 1300)

        // Démarrage de toutes les animations
        cardAnimatorSet.start()
        iconAnimatorSet.start()
        titleAnimation.start()
        subtitleAnimation.start()
        messageAnimation.start()
        rewardAnimation.start()
        buttonAnimation.start()

        // Animation de pulsation continue de l'icône après l'entrée
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500)
            startIconPulseAnimation()
            shineEffect.startAnimations()
        }
    }

    private fun createTextAnimation(view: View, startDelay: Long): AnimatorSet {
        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(view, "translationY", view.translationY, 0f),
                ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1f)
            )
            duration = 500
            this.startDelay = startDelay
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun startIconPulseAnimation() {
        val pulseAnimator = ObjectAnimator.ofFloat(achievementIcon, "scaleX", 1f, 1.15f, 1f)
        val pulseAnimatorY = ObjectAnimator.ofFloat(achievementIcon, "scaleY", 1f, 1.15f, 1f)

        val pulseAnimatorSet = AnimatorSet().apply {
            playTogether(pulseAnimator, pulseAnimatorY)
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Configurer la répétition sur les animateurs individuels
        pulseAnimator.repeatCount = ObjectAnimator.INFINITE
        pulseAnimatorY.repeatCount = ObjectAnimator.INFINITE

        pulseAnimatorSet.start()
    }

    private fun setupClickListeners() {
        continueButton.setOnClickListener {
            dismissWithAnimation()
        }

        // Fermeture en touchant en dehors (optionnel pour une meilleure UX)
        findViewById<View>(R.id.backgroundOverlay)?.setOnClickListener {
            // Ne pas fermer automatiquement - l'utilisateur doit cliquer sur le bouton
            // pour une meilleure expérience de célébration
        }
    }

    private fun dismissWithAnimation() {
        // Arrêter les effets visuels
        shineEffect.stopAnimations()

        val scaleDownX = ObjectAnimator.ofFloat(congratsCard, "scaleX", 1f, 0.8f)
        val scaleDownY = ObjectAnimator.ofFloat(congratsCard, "scaleY", 1f, 0.8f)
        val fadeOut = ObjectAnimator.ofFloat(congratsCard, "alpha", 1f, 0f)
        val slideDown = ObjectAnimator.ofFloat(congratsCard, "translationY", 0f, 100f)

        AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY, fadeOut, slideDown)
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onDismissCallback?.invoke()
                dismiss()
            }
        })
    }

    override fun onBackPressed() {
        dismissWithAnimation()
    }

    override fun dismiss() {
        // Nettoyer les ressources
        shineEffect.stopAnimations()
        soundManager.release()
        super.dismiss()
    }

    /**
     * Définit l'icône appropriée pour l'achievement
     */
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
