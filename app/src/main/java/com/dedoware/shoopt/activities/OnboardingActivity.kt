package com.dedoware.shoopt.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.dedoware.shoopt.R
import com.dedoware.shoopt.adapters.OnboardingAdapter
import com.dedoware.shoopt.models.OnboardingItem
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicators: LinearLayout
    private lateinit var skipButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var getStartedButton: MaterialButton
    private lateinit var onboardingAdapter: OnboardingAdapter

    private val onboardingItems = listOf(
        OnboardingItem(
            R.drawable.ic_scan_product,
            R.string.onboarding_step1_title,
            R.string.onboarding_step1_description
        ),
        OnboardingItem(
            R.drawable.ic_brain_memory,
            R.string.onboarding_step2_title,
            R.string.onboarding_step2_description
        ),
        OnboardingItem(
            R.drawable.ic_shopping_cart_confident,
            R.string.onboarding_step3_title,
            R.string.onboarding_step3_description
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        try {
            // Analytics pour l'ouverture de l'onboarding
            AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("Onboarding", "OnboardingActivity")

            initViews()
            setupViewPager()
            setupClickListeners()

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur dans OnboardingActivity.onCreate: ${e.message}")
            CrashlyticsManager.logException(e)
            // En cas d'erreur, on redirige vers l'écran de connexion
            redirectToLogin()
        }
    }

    private fun initViews() {
        viewPager = findViewById(R.id.onboarding_viewpager)
        pageIndicators = findViewById(R.id.page_indicators)
        skipButton = findViewById(R.id.btn_skip)
        nextButton = findViewById(R.id.btn_next)
        getStartedButton = findViewById(R.id.btn_get_started)
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingAdapter(onboardingItems)
        viewPager.adapter = onboardingAdapter

        // Créer les indicateurs de page customisés
        setupPageIndicators()

        // Listener pour changer les boutons selon la page
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonsVisibility(position)
                updatePageIndicators(position)

                // Analytics pour le changement de page
                val pageParams = Bundle().apply {
                    putString("action", "onboarding_page_view")
                    putString("category", "navigation")
                    putString("page_index", position.toString())
                }
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", pageParams)
            }
        })
    }

    private fun setupPageIndicators() {
        pageIndicators.removeAllViews()

        for (i in onboardingItems.indices) {
            val indicator = View(this)
            val params = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.indicator_width),
                resources.getDimensionPixelSize(R.dimen.indicator_height)
            )

            if (i > 0) {
                params.marginStart = resources.getDimensionPixelSize(R.dimen.indicator_margin)
            }

            indicator.layoutParams = params
            indicator.background = ContextCompat.getDrawable(this, R.drawable.page_indicator_inactive)
            pageIndicators.addView(indicator)
        }

        // Marquer le premier indicateur comme actif
        updatePageIndicators(0)
    }

    private fun updatePageIndicators(position: Int) {
        for (i in 0 until pageIndicators.childCount) {
            val indicator = pageIndicators.getChildAt(i)
            indicator.background = ContextCompat.getDrawable(
                this,
                if (i == position) R.drawable.page_indicator_active else R.drawable.page_indicator_inactive
            )
        }
    }

    private fun setupClickListeners() {
        skipButton.setOnClickListener {
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","onboarding_skip"); putString("category","button_click") })
            finishOnboarding()
        }

        nextButton.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem < onboardingItems.size - 1) {
                viewPager.currentItem = currentItem + 1
                AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","onboarding_next"); putString("category","button_click"); putString("from_page", currentItem.toString()) })
            }
        }

        getStartedButton.setOnClickListener {
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","onboarding_complete"); putString("category","button_click") })
            finishOnboarding()
        }
    }

    private fun updateButtonsVisibility(position: Int) {
        val isLastPage = position == onboardingItems.size - 1

        if (isLastPage) {
            skipButton.visibility = View.GONE
            nextButton.visibility = View.GONE
            getStartedButton.visibility = View.VISIBLE
        } else {
            skipButton.visibility = View.VISIBLE
            nextButton.visibility = View.VISIBLE
            getStartedButton.visibility = View.GONE
        }
    }

    private fun finishOnboarding() {
        try {
            // Marquer l'introduction comme terminée
            UserPreferences.setOnboardingCompleted(this, true)

            // Analytics pour la fin de l'onboarding
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", Bundle().apply { putString("action","onboarding_introduction_finished"); putString("category","completion") })

            // Vérifier si c'est un replay depuis les paramètres
            val isReplay = intent.getBooleanExtra("is_replay", false)

            if (isReplay) {
                // Si c'est un replay, ajouter un flag spécial pour forcer les spotlights
                val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("force_spotlights_on_next_resume", true).apply()

                CrashlyticsManager.log("OnboardingActivity: Replay completed, setting force_spotlights flag")
            }

            // Rediriger vers l'écran principal où les spotlights se déclencheront automatiquement
            redirectToMain()

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la finalisation de l'onboarding: ${e.message}")
            CrashlyticsManager.logException(e)
            redirectToLogin()
        }
    }

    private fun redirectToMain() {
        val intent = Intent(this, MainActivity::class.java)

        // Vérifier si c'est un replay depuis l'intent actuel de cette activité
        val isReplay = this.intent.getBooleanExtra("is_replay", false)
        if (isReplay) {
            // Au lieu de forcer une nouvelle instance, ajouter un flag pour forcer le refresh
            intent.putExtra("force_spotlight_refresh", true)
            // Utiliser FLAG_ACTIVITY_CLEAR_TOP pour revenir à MainActivity existante ou en créer une nouvelle
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            CrashlyticsManager.log("OnboardingActivity: Replay redirect - using CLEAR_TOP and SINGLE_TOP")
        }

        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    override fun onBackPressed() {
        if (viewPager.currentItem == 0) {
            // Si on est sur la première page, on ferme l'app
            super.onBackPressed()
        } else {
            // Sinon on revient à la page précédente
            viewPager.currentItem = viewPager.currentItem - 1
        }
    }
}
