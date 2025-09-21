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
 * Dialog moderne de félicitation pour le premier produit ajouté
 * Inclut des animations fluides, effets sonores, vibrations et design moderne
 */
class FirstProductCongratulationDialog(
    context: Context,
    private val onDismissCallback: (() -> Unit)? = null
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private lateinit var congratsCard: CardView
    private lateinit var trophyIcon: ImageView
    private lateinit var shineEffect: ShineEffectView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var messageText: TextView
    private lateinit var statsText: TextView
    private lateinit var continueButton: MaterialButton
    private lateinit var celebrationParticles: CelebrationParticleView

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
        trophyIcon = view.findViewById(R.id.trophyIcon)
        shineEffect = view.findViewById(R.id.shineEffect)
        titleText = view.findViewById(R.id.titleText)
        subtitleText = view.findViewById(R.id.subtitleText)
        messageText = view.findViewById(R.id.messageText)
        statsText = view.findViewById(R.id.statsText)
        continueButton = view.findViewById(R.id.continueButton)
        celebrationParticles = view.findViewById(R.id.celebrationParticles)

        // Configuration du contenu
        titleText.text = context.getString(R.string.first_product_congrats_title)
        subtitleText.text = context.getString(R.string.first_product_congrats_subtitle)
        messageText.text = context.getString(R.string.first_product_congrats_message)
        statsText.text = context.getString(R.string.first_product_stats_message)
        continueButton.text = context.getString(R.string.continue_shopping)

        // Icône du trophée
        trophyIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_trophy_gold))
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

        // Animation du trophée
        trophyIcon.scaleX = 0f
        trophyIcon.scaleY = 0f
        trophyIcon.rotation = -180f

        // Animation des textes
        titleText.alpha = 0f
        titleText.translationY = 50f
        subtitleText.alpha = 0f
        subtitleText.translationY = 30f
        messageText.alpha = 0f
        messageText.translationY = 50f
        statsText.alpha = 0f
        statsText.translationY = 30f
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

        // Animation du trophée avec rotation et rebond
        val trophyScaleX = ObjectAnimator.ofFloat(trophyIcon, "scaleX", 0f, 1.3f, 1f)
        val trophyScaleY = ObjectAnimator.ofFloat(trophyIcon, "scaleY", 0f, 1.3f, 1f)
        val trophyRotation = ObjectAnimator.ofFloat(trophyIcon, "rotation", -180f, 0f)

        val trophyAnimatorSet = AnimatorSet().apply {
            playTogether(trophyScaleX, trophyScaleY, trophyRotation)
            duration = 1000
            startDelay = 400
            interpolator = BounceInterpolator()
        }

        // Animations des textes en cascade
        val titleAnimation = createTextAnimation(titleText, 700)
        val subtitleAnimation = createTextAnimation(subtitleText, 850)
        val messageAnimation = createTextAnimation(messageText, 1000)
        val statsAnimation = createTextAnimation(statsText, 1150)
        val buttonAnimation = createTextAnimation(continueButton, 1300)

        // Démarrage de toutes les animations
        cardAnimatorSet.start()
        trophyAnimatorSet.start()
        titleAnimation.start()
        subtitleAnimation.start()
        messageAnimation.start()
        statsAnimation.start()
        buttonAnimation.start()

        // Animation de pulsation continue du trophée après l'entrée
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500)
            startTrophyPulseAnimation()
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

    private fun startTrophyPulseAnimation() {
        val pulseAnimator = ObjectAnimator.ofFloat(trophyIcon, "scaleX", 1f, 1.15f, 1f)
        val pulseAnimatorY = ObjectAnimator.ofFloat(trophyIcon, "scaleY", 1f, 1.15f, 1f)

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

        // Marquer la félicitation comme montrée
        firstProductManager.markCongratulationShown()

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
}
