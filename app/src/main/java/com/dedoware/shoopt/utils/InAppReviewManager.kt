package com.dedoware.shoopt.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.dedoware.shoopt.ShooptApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Gestion simple et claire de l'In-App Review (Play Core).
 * Responsabilités :
 * - S'assurer de la fréquence (1 demande tous les 3 mois)
 * - Ne jamais redemander après un refus explicite
 * - Respecter le toggle Remote Config
 * - Fournir un point d'appel unique depuis les Activities
 */
class InAppReviewManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "in_app_review_prefs"
        private const val KEY_LAST_SHOWN_TS = "last_shown_ts"
        private const val KEY_USER_DECLINED = "user_declined"
        private const val KEY_REQUEST_COUNT = "request_count"
        private const val KEY_SCAN_COUNT = "scan_count"
        private const val KEY_ANALYSE_OPEN_COUNT = "analyse_open_count"
        private const val KEY_LISTS_CREATED_COUNT = "lists_created_count"

        private const val ANALYSE_OPEN_THRESHOLD = 2
        private const val LISTS_CREATED_THRESHOLD = 3

        private val REQUEST_COOLDOWN_MS = TimeUnit.DAYS.toMillis(90) // ~3 mois

        @Volatile
        private var INSTANCE: InAppReviewManager? = null

        fun getInstance(context: Context): InAppReviewManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InAppReviewManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // Toggle via Remote Config (clé remote config)
    private val REMOTE_CONFIG_KEY = "in_app_review_enabled"

    /**
     * Vérifie si on peut afficher la demande selon les règles (cooldown, refus explicite, remote config)
     */
    fun canRequestReview(): Boolean {
        try {
            // Remote Config feature flag
            val enabled = remoteConfig.getBoolean(REMOTE_CONFIG_KEY)
            if (!enabled) return false

            if (prefs.getBoolean(KEY_USER_DECLINED, false)) return false

            val lastTs = prefs.getLong(KEY_LAST_SHOWN_TS, 0L)
            val now = System.currentTimeMillis()
            if (lastTs != 0L && now - lastTs < REQUEST_COOLDOWN_MS) return false

            return true
        } catch (e: Exception) {
            // En cas d'erreur, refuser prudemment
            return false
        }
    }

    /**
     * Appel simple pour tenter d'afficher la review. Activity requis pour afficher la pop-up système.
     * Enregistre des events Analytics.
     */
    fun requestReviewIfEligible(activity: Activity) {
        if (!canRequestReview()) return

        // Track that a request was attempted
        try {
            val bundle = Bundle().apply { putString("trigger_activity", activity::class.java.simpleName) }
            ShooptApplication.instance.analyticsService.logEvent("in_app_review_shown", bundle)
        } catch (e: Exception) {
            // Ignore analytics failure
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                android.util.Log.d("InAppReview", "Début requestReviewFlow")
                android.widget.Toast.makeText(activity, "Début demande avis...", android.widget.Toast.LENGTH_SHORT).show()
                val manager = ReviewManagerFactory.create(activity)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        android.util.Log.d("InAppReview", "requestReviewFlow success")
                        val reviewInfo = task.result
                        if (reviewInfo == null) {
                            android.util.Log.e("InAppReview", "reviewInfo est null")
                            android.widget.Toast.makeText(activity, "reviewInfo null", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.util.Log.d("InAppReview", "reviewInfo non null, lancement du flow")
                            android.widget.Toast.makeText(activity, "Lancement du prompt Google...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener { _ ->
                            android.util.Log.d("InAppReview", "Fin du flow Play Core (prompt affiché ou non)")
                            android.widget.Toast.makeText(activity, "Fin du flow Play Core", android.widget.Toast.LENGTH_SHORT).show()
                            // Marquer la date d'affichage seulement si le flow a été lancé
                            prefs.edit().putLong(KEY_LAST_SHOWN_TS, System.currentTimeMillis()).apply()
                            prefs.edit().putInt(KEY_REQUEST_COUNT, prefs.getInt(KEY_REQUEST_COUNT, 0) + 1).apply()
                            try {
                                ShooptApplication.instance.analyticsService.logEvent("in_app_review_completed", null)
                            } catch (e: Exception) {}
                            // Note: We don't know if user left a rating. Play Core doesn't expose that.
                        }
                    } else {
                        android.util.Log.e("InAppReview", "requestReviewFlow failed: ${task.exception}")
                        android.widget.Toast.makeText(activity, "Erreur Play Core: ${task.exception?.message}", android.widget.Toast.LENGTH_LONG).show()
                        // Fallback: request failed - do not penalize the user; clear last_shown so we can retry later
                        prefs.edit().putLong(KEY_LAST_SHOWN_TS, 0L).apply()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("InAppReview", "Exception globale: $e")
                android.widget.Toast.makeText(activity, "Exception: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                // En cas d'erreur, rollback timestamp pour permettre un nouvel essai
                try { prefs.edit().putLong(KEY_LAST_SHOWN_TS, 0L).apply() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Montre un petit message neutre avant la pop-up système (facultatif).
     * Si l'utilisateur accepte => lancer l'API In-App Review.
     * Si l'utilisateur refuse explicitement => marquer comme declined (jamais redemander).
     */
    fun showReviewPrompt(activity: Activity) {
        try {
            if (!canRequestReview()) return

            // Inflater le layout dédié au In-App Review
            val inflater = activity.layoutInflater
            val view = inflater.inflate(com.dedoware.shoopt.R.layout.dialog_in_app_review, null)

            val titleView = view.findViewById<android.widget.TextView>(com.dedoware.shoopt.R.id.dialog_title)
            val messageView = view.findViewById<android.widget.TextView>(com.dedoware.shoopt.R.id.dialog_message)
            val positiveBtn = view.findViewById<android.widget.Button>(com.dedoware.shoopt.R.id.btn_positive)
            val negativeBtn = view.findViewById<android.widget.Button>(com.dedoware.shoopt.R.id.btn_negative)

            // Au besoin on peut mettre à jour les textes via resources ici (i18n)
            try {
                titleView?.text = activity.getString(com.dedoware.shoopt.R.string.in_app_review_title)
                messageView?.text = activity.getString(com.dedoware.shoopt.R.string.in_app_review_message)
            } catch (_: Exception) { /* fallback to layout text */ }

            val dialog = AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(true)
                .create()

            positiveBtn?.setOnClickListener {
                try {
                    ShooptApplication.instance.analyticsService.logEvent("in_app_review_prepositive_click", null)
                } catch (_: Exception) {}

                requestReviewIfEligible(activity)
                dialog.dismiss()
            }

            negativeBtn?.setOnClickListener {
                try {
                    ShooptApplication.instance.analyticsService.logEvent("in_app_review_predecline", null)
                } catch (_: Exception) {}

                markUserDeclined()
                dialog.dismiss()
            }

            dialog.show()

            try {
                ShooptApplication.instance.analyticsService.logEvent("in_app_review_pre_shown", null)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            // Silence
        }
    }

    /**
     * Marque que l'utilisateur a décliné explicitement (ex : a fermé un dialogue préliminaire)
     * Après cet appel, nous ne redemanderons jamais.
     */
    fun markUserDeclined() {
        prefs.edit().putBoolean(KEY_USER_DECLINED, true).apply()
    }

    /**
     * Valeur utile pour debug / dashboard : nombre de demandes affichées
     */
    fun getRequestCount(): Int = prefs.getInt(KEY_REQUEST_COUNT, 0)

    /**
     * Notifie un événement dans l'application qui pourrait déclencher une demande d'avis.
     * Les événements connus sont : "scan_success", "analysis_open", "list_created"
     */
    fun notifyEvent(eventName: String, activity: Activity) {
        try {
            if (UserPreferences.isFirstLaunch(context)) return // Pas de demande dès la première utilisation

            when (eventName) {
                "scan_success" -> {
                    val new = prefs.getInt(KEY_SCAN_COUNT, 0) + 1
                    prefs.edit().putInt(KEY_SCAN_COUNT, new).apply()
                    // Un scan réussi suffit comme déclencheur MVP
                    if (canRequestReview()) {
                        // Montrer le prompt neutral avant la pop-up système (optionnel)
                        showReviewPrompt(activity)
                    }
                }
                "analysis_open" -> {
                    val new = prefs.getInt(KEY_ANALYSE_OPEN_COUNT, 0) + 1
                    prefs.edit().putInt(KEY_ANALYSE_OPEN_COUNT, new).apply()
                    if (new >= ANALYSE_OPEN_THRESHOLD && canRequestReview()) {
                        showReviewPrompt(activity)
                    }
                }
                "list_created" -> {
                    val new = prefs.getInt(KEY_LISTS_CREATED_COUNT, 0) + 1
                    prefs.edit().putInt(KEY_LISTS_CREATED_COUNT, new).apply()
                    if (new >= LISTS_CREATED_THRESHOLD && canRequestReview()) {
                        showReviewPrompt(activity)
                    }
                }
                else -> {
                    // Unknown event - ignore
                }
            }
        } catch (e: Exception) {
            // Silencieux en cas d'erreur pour ne pas impacter UX
        }
    }

    // Méthode de test pour remplacer le RemoteConfig par un fake si besoin
    @VisibleForTesting
    internal fun setRemoteConfigForTesting(rc: FirebaseRemoteConfig) {
        // Not implemented - placeholder for tests
    }
}
