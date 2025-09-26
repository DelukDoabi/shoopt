package com.dedoware.shoopt.analytics

import android.content.Context
import android.os.Bundle
import com.dedoware.shoopt.utils.UserPreferences
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Service centralisé pour gérer tous les événements d'analytics de l'application Shoopt.
 * Ce service utilise Firebase Analytics pour collecter des données d'usage conformément à RGPD.
 */
class AnalyticsService private constructor(private val context: Context) {

    private val firebaseAnalytics = Firebase.analytics
    private var isTrackingEnabled = false

    init {
        // Charger les préférences utilisateur concernant le tracking
        loadTrackingPreference()
    }

    companion object {
        // Singleton instance
        @Volatile
        private var instance: AnalyticsService? = null

        fun getInstance(context: Context): AnalyticsService {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsService(context.applicationContext).also { instance = it }
            }
        }

        // Constantes pour les noms d'événements
        // Onboarding
        const val EVENT_ONBOARDING_START = "onboarding_start"
        const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"
        const val EVENT_ONBOARDING_STEP = "onboarding_step"

        // Listes
        const val EVENT_LIST_CREATE = "list_create"
        const val EVENT_LIST_OPEN = "list_open"
        const val EVENT_LIST_DELETE = "list_delete"

        // Produits
        const val EVENT_PRODUCT_ADD_MANUAL = "product_add_manual"
        const val EVENT_PRODUCT_ADD_SCAN = "product_add_scan"
        const val EVENT_PRODUCT_MODIFY = "product_modify"
        const val EVENT_PRODUCT_DELETE = "product_delete"

        // Scan
        const val EVENT_SCAN_SUCCESS = "scan_success"
        const val EVENT_SCAN_FAILED = "scan_failed"

        // Suivi achats
        const val EVENT_PURCHASE_TRACKING = "purchase_tracking_item_check"

        // Analyse dépenses
        const val EVENT_ANALYSIS_OPEN = "analysis_open"

        // Paramètres
        const val EVENT_SETTINGS_DARK_MODE = "settings_dark_mode"
        const val EVENT_SETTINGS_LANGUAGE = "settings_language"
        const val EVENT_SETTINGS_NOTIFICATIONS = "settings_notifications"
        const val EVENT_SETTINGS_ANALYTICS_OPT_OUT = "settings_analytics_opt_out"
        const val EVENT_SETTINGS_ANALYTICS_OPT_IN = "settings_analytics_opt_in"

        // Notifications
        const val EVENT_NOTIFICATION_RECEIVED = "notification_received"
        const val EVENT_NOTIFICATION_CLICKED = "notification_clicked"

        // Constantes pour les paramètres
        const val PARAM_LIST_ID = "list_id"
        const val PARAM_LIST_SIZE = "list_size"
        const val PARAM_PRODUCT_ID = "product_id"
        const val PARAM_PRODUCT_NAME = "product_name"
        const val PARAM_SCAN_TYPE = "scan_type"
        const val PARAM_STEP_NAME = "step_name"
        const val PARAM_LANGUAGE = "language"
        const val PARAM_MODE = "mode"
        const val PARAM_ENABLED = "enabled"
        const val PARAM_NOTIFICATION_TYPE = "notification_type"
        const val PARAM_SESSION_DURATION = "session_duration"
        const val PARAM_SCREEN_NAME = "screen_name"

        // PARAMÈTRES POUR LES DONATIONS
        const val EVENT_DONATION_SUCCESS = "donation_success"
        const val EVENT_DONATION_CANCEL = "donation_cancel"
        const val EVENT_DONATION_ATTEMPT = "donation_attempt"
        const val EVENT_DONATION_LAUNCH = "donation_launch"
        const val EVENT_DONATION_PRODUCT_DETAILS = "donation_product_details"
        const val EVENT_DONATION_BILLING_READY = "donation_billing_ready"
        const val EVENT_DONATION_FAILURE = "donation_failure"
        const val EVENT_DONATION_CONSUME = "donation_consume"
        const val EVENT_DONATION_PRICE_FALLBACK = "donation_price_fallback"
        const val EVENT_DONATION_TIMING_ATTEMPT_TO_LAUNCH = "donation_timing_attempt_to_launch"
        const val EVENT_DONATION_TIMING_LAUNCH_TO_PURCHASE = "donation_timing_launch_to_purchase"
         const val PARAM_DONATION_AMOUNT_CENTS = "amount_cents"
         const val PARAM_PRODUCT_PRICE_FORMATTED = "product_price_formatted"
         const val PARAM_CURRENCY = "currency"
         const val PARAM_ERROR = "error_reason"
     }

    /**
     * Charge les préférences utilisateur concernant le tracking
     */
    private fun loadTrackingPreference() {
        isTrackingEnabled = UserPreferences.isAnalyticsEnabled(context)
        setAnalyticsCollectionEnabled(isTrackingEnabled)
    }

    /**
     * Active le tracking des données analytics
     */
    fun enableTracking() {
        isTrackingEnabled = true
        UserPreferences.setAnalyticsEnabled(context, true)
        setAnalyticsCollectionEnabled(true)

        // Tracker l'événement de changement de préférence (opt-in)
        logEvent(EVENT_SETTINGS_ANALYTICS_OPT_IN, Bundle().apply {
            putBoolean(PARAM_ENABLED, true)
        })
    }

    /**
     * Désactive le tracking des données analytics
     */
    fun disableTracking() {
        // Log l'événement d'opt-out avant de couper la collecte afin qu'il soit envoyé
        firebaseAnalytics.logEvent(EVENT_SETTINGS_ANALYTICS_OPT_OUT, Bundle().apply {
            putBoolean(PARAM_ENABLED, false)
        })

        isTrackingEnabled = false
        UserPreferences.setAnalyticsEnabled(context, false)
        setAnalyticsCollectionEnabled(false)
    }

    /**
     * Vérifie si le tracking est activé
     */
    fun isTrackingEnabled(): Boolean {
        return isTrackingEnabled
    }

    /**
     * Active ou désactive la collecte de données Firebase Analytics
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
    }

    /**
     * Enregistre un événement d'analytics
     */
    fun logEvent(eventName: String, params: Bundle? = null) {
        if (isTrackingEnabled) {
            firebaseAnalytics.logEvent(eventName, params)
        }
    }

    /**
     * Définit l'ID utilisateur pour les analyses
     */
    fun setUserId(userId: String?) {
        if (isTrackingEnabled) {
            firebaseAnalytics.setUserId(userId)
        }
    }

    /**
     * Définit une propriété utilisateur
     */
    fun setUserProperty(name: String, value: String?) {
        if (isTrackingEnabled) {
            firebaseAnalytics.setUserProperty(name, value)
        }
    }

    // --- Méthodes spécifiques pour traquer les événements ---

    // ONBOARDING
    fun trackOnboardingStart() {
        logEvent(EVENT_ONBOARDING_START)
    }

    fun trackOnboardingStep(stepName: String) {
        val params = Bundle().apply {
            putString(PARAM_STEP_NAME, stepName)
        }
        logEvent(EVENT_ONBOARDING_STEP, params)
    }

    fun trackOnboardingComplete() {
        logEvent(EVENT_ONBOARDING_COMPLETE)
    }

    // LISTES
    fun trackListCreate(listId: String, listSize: Int = 0) {
        val params = Bundle().apply {
            putString(PARAM_LIST_ID, listId)
            putInt(PARAM_LIST_SIZE, listSize)
        }
        logEvent(EVENT_LIST_CREATE, params)
    }

    fun trackListOpen(listId: String, listSize: Int) {
        val params = Bundle().apply {
            putString(PARAM_LIST_ID, listId)
            putInt(PARAM_LIST_SIZE, listSize)
        }
        logEvent(EVENT_LIST_OPEN, params)
    }

    fun trackListDelete(listId: String) {
        val params = Bundle().apply {
            putString(PARAM_LIST_ID, listId)
        }
        logEvent(EVENT_LIST_DELETE, params)
    }

    // PRODUITS
    fun trackProductAddManual(productId: String, productName: String) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putString(PARAM_PRODUCT_NAME, productName)
        }
        logEvent(EVENT_PRODUCT_ADD_MANUAL, params)
    }

    fun trackProductAddScan(productId: String, productName: String) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putString(PARAM_PRODUCT_NAME, productName)
        }
        logEvent(EVENT_PRODUCT_ADD_SCAN, params)
    }

    fun trackProductModify(productId: String, productName: String) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putString(PARAM_PRODUCT_NAME, productName)
        }
        logEvent(EVENT_PRODUCT_MODIFY, params)
    }

    fun trackProductDelete(productId: String, productName: String) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putString(PARAM_PRODUCT_NAME, productName)
        }
        logEvent(EVENT_PRODUCT_DELETE, params)
    }

    // SCAN
    fun trackScanSuccess(scanType: String) {
        val params = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
        }
        logEvent(EVENT_SCAN_SUCCESS, params)
    }

    fun trackScanFailed(scanType: String, errorReason: String) {
        val params = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
            putString("error_reason", errorReason)
        }
        logEvent(EVENT_SCAN_FAILED, params)
    }

    // SUIVI ACHATS
    fun trackPurchaseItemCheck(productId: String, productName: String, isChecked: Boolean) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putString(PARAM_PRODUCT_NAME, productName)
            putBoolean("is_checked", isChecked)
        }
        logEvent(EVENT_PURCHASE_TRACKING, params)
    }

    // ANALYSE DÉPENSES
    fun trackAnalysisOpen() {
        logEvent(EVENT_ANALYSIS_OPEN)
    }

    // PARAMÈTRES
    fun trackDarkModeToggle(isDarkMode: Boolean) {
        val params = Bundle().apply {
            putBoolean(PARAM_ENABLED, isDarkMode)
        }
        logEvent(EVENT_SETTINGS_DARK_MODE, params)
    }

    fun trackLanguageChange(language: String) {
        val params = Bundle().apply {
            putString(PARAM_LANGUAGE, language)
        }
        logEvent(EVENT_SETTINGS_LANGUAGE, params)
        // Mettre à jour également la propriété utilisateur pour la segmentation
        setUserProperty("user_language", language)
    }

    fun trackNotificationsToggle(enabled: Boolean) {
        val params = Bundle().apply {
            putBoolean(PARAM_ENABLED, enabled)
        }
        logEvent(EVENT_SETTINGS_NOTIFICATIONS, params)
    }

    // NOTIFICATIONS
    fun trackNotificationReceived(notificationType: String) {
        val params = Bundle().apply {
            putString(PARAM_NOTIFICATION_TYPE, notificationType)
        }
        logEvent(EVENT_NOTIFICATION_RECEIVED, params)
    }

    fun trackNotificationClicked(notificationType: String) {
        val params = Bundle().apply {
            putString(PARAM_NOTIFICATION_TYPE, notificationType)
        }
        logEvent(EVENT_NOTIFICATION_CLICKED, params)
    }

    // SESSION
    fun trackScreenView(screenName: String, screenClass: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        // Utiliser logEvent pour respecter la préférence de tracking
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    // DONATIONS
    fun trackDonationSuccess(amountCents: Long?) {
        val params = Bundle().apply {
            amountCents?.let { putLong(PARAM_DONATION_AMOUNT_CENTS, it) }
        }
        logEvent(EVENT_DONATION_SUCCESS, params)
        // Mettre à jour des propriétés utilisateur utiles pour la segmentation
        try {
            setUserProperty("has_donated", "true")
            amountCents?.let { setUserProperty("last_donation_cents", it.toString()) }
        } catch (_: Exception) { /* safe fallback */ }
    }

    fun trackDonationCancel() {
        logEvent(EVENT_DONATION_CANCEL, null)
    }

    // DONATION: attempt (user clicked a donate button)
    fun trackDonationAttempt(productId: String, formattedPrice: String?) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            formattedPrice?.let { putString(PARAM_PRODUCT_PRICE_FORMATTED, it) }
        }
        logEvent(EVENT_DONATION_ATTEMPT, params)
    }

    // Donation: product details loaded from Play
    fun trackDonationProductDetails(productId: String, formattedPrice: String?, currency: String?, amountCents: Long?) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            formattedPrice?.let { putString(PARAM_PRODUCT_PRICE_FORMATTED, it) }
            currency?.let { putString(PARAM_CURRENCY, it) }
            amountCents?.let { putLong(PARAM_DONATION_AMOUNT_CENTS, it) }
        }
        logEvent(EVENT_DONATION_PRODUCT_DETAILS, params)
    }

    // Billing ready for donations
    fun trackDonationBillingReady() {
        logEvent(EVENT_DONATION_BILLING_READY)
    }

    // Donation: billing flow launched
    fun trackDonationLaunch(productId: String) {
        val params = Bundle().apply { putString(PARAM_PRODUCT_ID, productId) }
        logEvent(EVENT_DONATION_LAUNCH, params)
    }

    // Donation failure with reason
    fun trackDonationFailure(productId: String?, reason: String?) {
        val params = Bundle().apply {
            productId?.let { putString(PARAM_PRODUCT_ID, it) }
            reason?.let { putString(PARAM_ERROR, it) }
        }
        logEvent(EVENT_DONATION_FAILURE, params)
    }

    // Donation consume/acknowledge tracking
    fun trackDonationConsume(amountCents: Long?, success: Boolean, productId: String?) {
        val params = Bundle().apply {
            amountCents?.let { putLong(PARAM_DONATION_AMOUNT_CENTS, it) }
            productId?.let { putString(PARAM_PRODUCT_ID, it) }
            putBoolean("success", success)
        }
        logEvent(EVENT_DONATION_CONSUME, params)
    }

    // Track when we used fallback price (Play did not return formatted price)
    fun trackDonationPriceFallback(productId: String, fallbackSource: String? = null) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            fallbackSource?.let { putString("fallback_source", it) }
        }
        logEvent(EVENT_DONATION_PRICE_FALLBACK, params)
    }

    // Timing: from user attempt (click) to billing flow launch
    fun trackDonationTimingAttemptToLaunch(productId: String, durationMs: Long) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putLong("duration_ms", durationMs)
        }
        logEvent(EVENT_DONATION_TIMING_ATTEMPT_TO_LAUNCH, params)
    }

    // Timing: from billing flow launch to purchase completion
    fun trackDonationTimingLaunchToPurchase(productId: String, durationMs: Long) {
        val params = Bundle().apply {
            putString(PARAM_PRODUCT_ID, productId)
            putLong("duration_ms", durationMs)
        }
        logEvent(EVENT_DONATION_TIMING_LAUNCH_TO_PURCHASE, params)
    }

}
