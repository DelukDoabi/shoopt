package com.dedoware.shoopt.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.dedoware.shoopt.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Vue de contenu moderne pour afficher les informations du spotlight
 */
class SpotlightContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val titleText: TextView
    private val descriptionText: TextView
    private val nextButton: MaterialButton
    private val skipButton: MaterialButton
    private val buttonContainer: LinearLayout

    private var onNextClickListener: (() -> Unit)? = null
    private var onSkipClickListener: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.spotlight_content, this, true)

        titleText = findViewById(R.id.spotlight_title)
        descriptionText = findViewById(R.id.spotlight_description)
        nextButton = findViewById(R.id.btn_spotlight_next)
        skipButton = findViewById(R.id.btn_spotlight_skip)
        buttonContainer = findViewById(R.id.spotlight_buttons)

        setupClickListeners()
        setupInitialState()
    }

    private fun setupClickListeners() {
        nextButton.setOnClickListener {
            onNextClickListener?.invoke()
        }

        skipButton.setOnClickListener {
            onSkipClickListener?.invoke()
        }
    }

    private fun setupInitialState() {
        // Animation d'entrée fluide
        alpha = 0f
        scaleX = 0.8f
        scaleY = 0.8f
    }

    fun setContent(
        title: String,
        description: String,
        showNextButton: Boolean = true,
        showSkipButton: Boolean = true,
        nextButtonText: String = context.getString(R.string.spotlight_next),
        skipButtonText: String = context.getString(R.string.spotlight_skip)
    ) {
        titleText.text = title
        descriptionText.text = description

        nextButton.isVisible = showNextButton
        nextButton.text = nextButtonText

        skipButton.isVisible = showSkipButton
        skipButton.text = skipButtonText

        buttonContainer.isVisible = showNextButton || showSkipButton
    }

    fun setOnNextClickListener(listener: () -> Unit) {
        this.onNextClickListener = listener
    }

    fun setOnSkipClickListener(listener: () -> Unit) {
        this.onSkipClickListener = listener
    }

    fun animateIn() {
        val animatorSet = AnimatorSet()

        val fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)
        val scaleXIn = ObjectAnimator.ofFloat(this, "scaleX", 0.8f, 1f)
        val scaleYIn = ObjectAnimator.ofFloat(this, "scaleY", 0.8f, 1f)
        val translateY = ObjectAnimator.ofFloat(this, "translationY", 50f, 0f)

        animatorSet.apply {
            playTogether(fadeIn, scaleXIn, scaleYIn, translateY)
            duration = 300L
            startDelay = 150L // Léger délai après l'ouverture du spotlight
            start()
        }
    }

    fun animateOut(onComplete: () -> Unit) {
        val animatorSet = AnimatorSet()

        val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)
        val scaleXOut = ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.8f)
        val scaleYOut = ObjectAnimator.ofFloat(this, "scaleY", 1f, 0.8f)

        animatorSet.apply {
            playTogether(fadeOut, scaleXOut, scaleYOut)
            duration = 200L
            start()
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })
    }
}
