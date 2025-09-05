package com.dedoware.shoopt.models

import android.view.View
import androidx.annotation.StringRes

/**
 * Modèle représentant un élément à mettre en surbrillance dans le système de spotlight
 */
data class SpotlightItem(
    val targetView: View,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val shape: SpotlightShape = SpotlightShape.CIRCLE,
    val dismissOnTouchOutside: Boolean = true,
    val dismissOnTargetTouch: Boolean = true
)

/**
 * Formes disponibles pour le spotlight
 */
enum class SpotlightShape {
    CIRCLE,
    ROUNDED_RECTANGLE
}
