package com.dedoware.shoopt.activities

import android.content.pm.ActivityInfo
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Activité personnalisée pour la capture de codes-barres en mode paysage (horizontal).
 * Cette classe étend CaptureActivity et force l'orientation en mode paysage.
 */
class HorizontalCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Définit l'orientation de l'activité en mode paysage
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
