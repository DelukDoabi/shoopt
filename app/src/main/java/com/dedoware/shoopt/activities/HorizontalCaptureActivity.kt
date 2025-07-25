package com.dedoware.shoopt.activities

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import com.dedoware.shoopt.R
import com.dedoware.shoopt.scanner.HorizontalViewfinderView
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * Activité personnalisée pour la capture de codes-barres en mode paysage (horizontal).
 * Cette classe étend CaptureActivity et force l'orientation en mode paysage avec une ligne de scan horizontale.
 */
class HorizontalCaptureActivity : CaptureActivity() {
    private lateinit var horizontalViewfinderView: HorizontalViewfinderView
    private var animator: ValueAnimator? = null
    private lateinit var orientationEventListener: OrientationEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force le mode paysage pour l'activité de scan
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Utilise notre mise en page personnalisée
        setContentView(R.layout.horizontal_scanner)

        // Configure la détection des changements d'orientation
        setupOrientationListener()

        // Récupère notre ViewfinderView personnalisé
        val decoratedBarcodeView = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
        decoratedBarcodeView?.let {
            val viewFinder = it.findViewById<HorizontalViewfinderView>(R.id.zxing_viewfinder_view)
            if (viewFinder != null) {
                horizontalViewfinderView = viewFinder
                // Démarre l'animation de la ligne de scan
                startScanLineAnimation()
            }
        }
    }

    /**
     * Configure un écouteur pour détecter les changements d'orientation de l'appareil
     */
    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                // Vérifie si l'orientation a changé significativement
                if (orientation != ORIENTATION_UNKNOWN) {
                    val rotation = when {
                        orientation > 315 || orientation <= 45 -> Surface.ROTATION_0
                        orientation > 45 && orientation <= 135 -> Surface.ROTATION_90
                        orientation > 135 && orientation <= 225 -> Surface.ROTATION_180
                        else -> Surface.ROTATION_270
                    }

                    // Force le mode paysage quelle que soit l'orientation physique
                    if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            }
        }

        // Activer l'écouteur si le capteur est disponible
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }

    /**
     * Anime la ligne de scan horizontale pour donner un effet de balayage
     */
    private fun startScanLineAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            duration = 2000L // 2 secondes pour un balayage complet
            addUpdateListener { animation ->
                val alpha = animation.animatedValue as Float
                horizontalViewfinderView.setScanLineAlpha(alpha)
            }
            start()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Force le mode paysage même si la configuration change
        if (newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onPause() {
        super.onPause()
        // Arrête l'animation quand l'activité est mise en pause
        animator?.cancel()
        orientationEventListener.disable()
    }

    override fun onResume() {
        super.onResume()
        // Relance l'animation quand l'activité reprend
        animator?.start()
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }

        // Force le mode paysage à chaque reprise de l'activité
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Garder l'écran allumé pendant le scan
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
