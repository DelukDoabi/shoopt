package com.dedoware.shoopt.utils

import android.content.Context
import android.os.Bundle
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.analytics.AnalyticsService

/**
 * Deprecated compatibility façade — délègue toutes les méthodes vers AnalyticsService.
 * L'objectif est de centraliser le tracking dans AnalyticsService; cette façade reste
 * temporaire pour éviter une régression si une référence a été oubliée.
 */
@Deprecated("Use AnalyticsService directly: AnalyticsService.getInstance(context)")
object AnalyticsManager {

    fun initialize(isDebug: Boolean) {
        // Si l'Application fournit un contexte global, on l'utilise
        try {
            val ctx = ShooptApplication.instance
            if (isDebug) {
                AnalyticsService.getInstance(ctx).setAnalyticsCollectionEnabled(false)
            } else {
                // Laisser AnalyticsService gérer la préférence utilisateur
                AnalyticsService.getInstance(ctx)
            }
        } catch (_: Exception) {
            // noop
        }
    }

    fun initialize(context: Context, isDebug: Boolean = false) {
        val svc = AnalyticsService.getInstance(context.applicationContext)
        if (isDebug) svc.setAnalyticsCollectionEnabled(false)
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).setAnalyticsCollectionEnabled(enabled)
        } catch (_: Exception) {
        }
    }

    fun setUserId(userId: String?) {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).setUserId(userId)
        } catch (_: Exception) {
        }
    }

    fun clearUserId() {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).setUserId(null)
        } catch (_: Exception) {
        }
    }

    fun logScreenView(screenName: String, screenClass: String) {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView(screenName, screenClass)
        } catch (_: Exception) {
        }
    }

    fun logUserAction(action: String, category: String, additionalParams: Map<String, Any>? = null) {
        try {
            val params = Bundle().apply {
                putString("action", action)
                putString("category", category)
                additionalParams?.forEach { (k, v) ->
                    when (v) {
                        is String -> putString(k, v)
                        is Int -> putInt(k, v)
                        is Long -> putLong(k, v)
                        is Double -> putDouble(k, v)
                        is Float -> putFloat(k, v)
                        is Boolean -> putBoolean(k, v)
                    }
                }
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", params)
        } catch (_: Exception) {
        }
    }

    fun logAuthEvent(method: String, success: Boolean) {
        try {
            val params = Bundle().apply {
                putString("method", method)
                putBoolean("success", success)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("sign_up", params)
        } catch (_: Exception) {
        }
    }

    fun logFeatureUsage(featureName: String, action: String) {
        try {
            val params = Bundle().apply {
                putString("feature_name", featureName)
                putString("action", action)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("feature_usage", params)
        } catch (_: Exception) {
        }
    }

    fun logPerformanceEvent(eventName: String, durationMs: Long) {
        try {
            val params = Bundle().apply { putLong("duration_ms", durationMs) }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent(eventName, params)
        } catch (_: Exception) {
        }
    }

    fun setUserProperty(name: String, value: String) {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).setUserProperty(name, value)
        } catch (_: Exception) {
        }
    }

    fun logCustomEvent(eventName: String, params: Bundle?) {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent(eventName, params)
        } catch (_: Exception) {
        }
    }

    fun logSelectContent(contentType: String, itemId: String, itemName: String? = null) {
        try {
            val params = Bundle().apply {
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.CONTENT_TYPE, contentType)
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_ID, itemId)
                itemName?.let { putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_NAME, it) }
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SELECT_CONTENT, params)
        } catch (_: Exception) {
        }
    }

    fun logEvent(name: String, params: Bundle?) {
        try {
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent(name, params)
        } catch (_: Exception) {
        }
    }

    fun trackEvent(eventName: String, parameters: Map<String, String>? = null) {
        try {
            val params = Bundle().apply {
                parameters?.forEach { (k, v) -> putString(k, v) }
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent(eventName, params)
        } catch (_: Exception) {
        }
    }
}
