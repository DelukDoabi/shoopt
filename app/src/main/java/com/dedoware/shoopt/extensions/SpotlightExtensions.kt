package com.dedoware.shoopt.extensions

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import com.dedoware.shoopt.models.SpotlightItem
import com.dedoware.shoopt.models.SpotlightShape
import com.dedoware.shoopt.utils.SpotlightManager

/**
 * Extensions pour simplifier l'utilisation du système de spotlight dans les activités
 */

/**
 * Démarre un tour de spotlight pour cette activité
 */
fun Activity.startSpotlightTour(
    spotlightItems: List<SpotlightItem>,
    onComplete: (() -> Unit)? = null
) {
    val screenKey = this.javaClass.simpleName

    SpotlightManager.getInstance(this)
        .setSpotlightItems(spotlightItems)
        .setOnCompleteListener { onComplete?.invoke() }
        .start(screenKey)
}

/**
 * Crée facilement un SpotlightItem
 */
fun Activity.createSpotlightItem(
    targetView: View,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    shape: SpotlightShape = SpotlightShape.CIRCLE,
    dismissOnTouchOutside: Boolean = true,
    dismissOnTargetTouch: Boolean = true
): SpotlightItem {
    return SpotlightItem(
        targetView = targetView,
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        shape = shape,
        dismissOnTouchOutside = dismissOnTouchOutside,
        dismissOnTargetTouch = dismissOnTargetTouch
    )
}

/**
 * Vérifie si le spotlight est disponible pour cette activité
 */
fun Activity.isSpotlightAvailable(): Boolean {
    val screenKey = this.javaClass.simpleName
    return SpotlightManager.shouldShowSpotlight(this, screenKey)
}

/**
 * Arrête le spotlight actuel s'il est en cours
 */
fun Activity.stopSpotlight() {
    SpotlightManager.getInstance(this).stop()
}
