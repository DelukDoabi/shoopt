package com.dedoware.shoopt.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.util.Date

/**
 * Classe qui gère les statistiques de consentement analytics
 * Cette classe enregistre le nombre d'acceptations et de refus du consentement analytics
 * même lorsque le tracking général est désactivé.
 */
class ConsentTracker private constructor(private val context: Context) {

    private val consentPrefs: SharedPreferences = context.getSharedPreferences(
        CONSENT_STATS_PREFS, Context.MODE_PRIVATE
    )

    // Firebase Analytics est toujours utilisé pour suivre uniquement les événements de consentement,
    // même si l'utilisateur a refusé les autres suivis
    private val firebaseAnalytics = Firebase.analytics

    companion object {
        private const val CONSENT_STATS_PREFS = "consent_stats_prefs"
        private const val KEY_ACCEPTS_COUNT = "consent_accepts_count"
        private const val KEY_DECLINES_COUNT = "consent_declines_count"
        private const val KEY_LAST_ACCEPT_DATE = "last_accept_date"
        private const val KEY_LAST_DECLINE_DATE = "last_decline_date"

        // Les événements de consentement sont toujours suivis, même si l'utilisateur refuse le suivi général
        private const val EVENT_CONSENT_ACCEPTED = "consent_decision"
        private const val PARAM_DECISION = "decision"
        private const val DECISION_ACCEPTED = "accepted"
        private const val DECISION_DECLINED = "declined"

        @Volatile
        private var INSTANCE: ConsentTracker? = null

        fun getInstance(context: Context): ConsentTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConsentTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Enregistre une acceptation du consentement analytics
     */
    fun trackConsentAccepted() {
        // Incrémenter le compteur d'acceptations
        val currentCount = consentPrefs.getInt(KEY_ACCEPTS_COUNT, 0)
        consentPrefs.edit()
            .putInt(KEY_ACCEPTS_COUNT, currentCount + 1)
            .putLong(KEY_LAST_ACCEPT_DATE, Date().time)
            .apply()

        // Envoyer un événement à Firebase (même si le tracking est désactivé)
        // Cet événement est le seul qui sera envoyé si l'utilisateur refuse le suivi général
        val params = Bundle().apply {
            putString(PARAM_DECISION, DECISION_ACCEPTED)
        }
        firebaseAnalytics.logEvent(EVENT_CONSENT_ACCEPTED, params)
    }

    /**
     * Enregistre un refus du consentement analytics
     */
    fun trackConsentDeclined() {
        // Incrémenter le compteur de refus
        val currentCount = consentPrefs.getInt(KEY_DECLINES_COUNT, 0)
        consentPrefs.edit()
            .putInt(KEY_DECLINES_COUNT, currentCount + 1)
            .putLong(KEY_LAST_DECLINE_DATE, Date().time)
            .apply()

        // Envoyer un événement à Firebase pour suivre les refus
        // C'est le seul événement qui sera envoyé si l'utilisateur refuse le suivi général
        val params = Bundle().apply {
            putString(PARAM_DECISION, DECISION_DECLINED)
        }
        firebaseAnalytics.logEvent(EVENT_CONSENT_ACCEPTED, params)
    }

    /**
     * Récupère le nombre d'acceptations du consentement analytics
     */
    fun getAcceptsCount(): Int {
        return consentPrefs.getInt(KEY_ACCEPTS_COUNT, 0)
    }

    /**
     * Récupère le nombre de refus du consentement analytics
     */
    fun getDeclinesCount(): Int {
        return consentPrefs.getInt(KEY_DECLINES_COUNT, 0)
    }

    /**
     * Récupère la date de la dernière acceptation
     * @return La date de la dernière acceptation ou null si aucune acceptation n'a été enregistrée
     */
    fun getLastAcceptDate(): Date? {
        val time = consentPrefs.getLong(KEY_LAST_ACCEPT_DATE, -1)
        return if (time != -1L) Date(time) else null
    }

    /**
     * Récupère la date du dernier refus
     * @return La date du dernier refus ou null si aucun refus n'a été enregistré
     */
    fun getLastDeclineDate(): Date? {
        val time = consentPrefs.getLong(KEY_LAST_DECLINE_DATE, -1)
        return if (time != -1L) Date(time) else null
    }

    /**
     * Récupère les statistiques de consentement sous forme de chaîne formatée
     */
    fun getConsentStats(): String {
        val acceptsCount = getAcceptsCount()
        val declinesCount = getDeclinesCount()
        val totalCount = acceptsCount + declinesCount

        val acceptRate = if (totalCount > 0) {
            String.format("%.1f", acceptsCount * 100.0 / totalCount)
        } else "0.0"

        val declineRate = if (totalCount > 0) {
            String.format("%.1f", declinesCount * 100.0 / totalCount)
        } else "0.0"

        val lastAcceptDate = getLastAcceptDate()?.let {
            android.text.format.DateFormat.getDateFormat(context).format(it)
        } ?: "N/A"

        val lastDeclineDate = getLastDeclineDate()?.let {
            android.text.format.DateFormat.getDateFormat(context).format(it)
        } ?: "N/A"

        return """
            Statistiques de consentement analytics:
            - Nombre total de décisions: $totalCount
            - Acceptations: $acceptsCount ($acceptRate%)
            - Refus: $declinesCount ($declineRate%)
            - Dernière acceptation: $lastAcceptDate
            - Dernier refus: $lastDeclineDate
        """.trimIndent()
    }

    /**
     * Réinitialise les statistiques de consentement
     * Utile pour les tests ou pour réinitialiser les compteurs
     */
    fun resetStats() {
        consentPrefs.edit().clear().apply()
    }
}
