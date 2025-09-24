package com.dedoware.shoopt.scanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.dedoware.shoopt.R
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.dedoware.shoopt.gamification.manager.SimplifiedGamificationManager
import com.dedoware.shoopt.gamification.manager.AchievementCelebrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.ShooptApplication

class BarcodeScannerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BarcodeScannerActivity"
        const val BARCODE_RESULT = "barcode_result"
    }

    private lateinit var cameraExecutor: ExecutorService
    private var flashEnabled = false

    // Vues
    private lateinit var previewView: PreviewView
    private lateinit var scanLine: View
    private lateinit var scanFrame: View
    private lateinit var scanAreaContainer: View
    private lateinit var closeButton: ImageButton
    private lateinit var flashButton: ImageButton

    // Animation pour la ligne de scan
    private lateinit var scanLineAnimator: ObjectAnimator

    // Barcode scanner
    private lateinit var barcodeScanner: BarcodeScanner

    // Gestionnaire de célébrations
    private lateinit var achievementCelebrationManager: AchievementCelebrationManager

    // Demande de permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    // Add flag to prevent multiple detections
    private var isProcessingBarcode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        // Initialiser le gestionnaire de célébrations pour cette activité
        achievementCelebrationManager = AchievementCelebrationManager(this)

        // Initialiser les vues
        previewView = findViewById(R.id.previewView)
        scanLine = findViewById(R.id.scanLine)
        scanFrame = findViewById(R.id.scanFrame)
        scanAreaContainer = findViewById(R.id.scanAreaContainer)
        closeButton = findViewById(R.id.closeButton)
        flashButton = findViewById(R.id.flashButton)

        // Initialiser le scanner de codes-barres avec les formats supportés
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)

        // Initialiser l'exécuteur de la caméra
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configurer l'animation de la ligne de scan
        setupScanLineAnimation()

        // Configurer les boutons
        setupButtons()

        // Vérifier les permissions de la caméra
        requestCameraPermission()
    }

    private fun setupButtons() {
        closeButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            // Tracké comme échec si l'utilisateur annule
            try {
                AnalyticsService.getInstance(ShooptApplication.instance).trackScanFailed("barcode", "user_cancelled")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to track scan cancellation: ${e.message}")
            }
            finish()
        }

        flashButton.setOnClickListener {
            toggleFlash()
        }
    }

    private fun setupScanLineAnimation() {
        // Attendre que le conteneur soit mesuré
        scanAreaContainer.post {
            // Réinitialiser toute animation précédente
            if (::scanLineAnimator.isInitialized) {
                scanLineAnimator.cancel()
            }

            // La ligne commence tout en haut du conteneur
            scanLine.translationY = 0f

            // Calculer la distance exacte à parcourir (hauteur du conteneur moins hauteur de la ligne)
            val containerHeight = scanAreaContainer.height.toFloat()
            val lineHeight = scanLine.height.toFloat()
            val distance = containerHeight - lineHeight

            // Créer l'animation avec les limites précises du conteneur
            scanLineAnimator = ObjectAnimator.ofFloat(
                scanLine,
                "translationY",
                0f,  // Démarre du haut du conteneur
                distance  // S'arrête exactement au bas du conteneur
            ).apply {
                duration = 2000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val barcode = barcodes.first()
                            barcode.rawValue?.let { value ->
                                Log.d(TAG, "Barcode detected: $value")
                                returnBarcodeResult(value)
                            }
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )

                // Pour le contrôle du flash
                flashButton.setOnClickListener {
                    flashEnabled = !flashEnabled
                    camera.cameraControl.enableTorch(flashEnabled)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        // Cette fonction est gérée dans le callback du binding de la caméra
    }

    private fun returnBarcodeResult(barcodeValue: String) {
        // Prevent multiple calls
        if (isProcessingBarcode) return
        isProcessingBarcode = true

        runOnUiThread {
            // Stop camera analysis immediately to prevent more detections
            if (::barcodeScanner.isInitialized) {
                barcodeScanner.close()
            }

            // Tracker le succès du scan
            try {
                AnalyticsService.getInstance(ShooptApplication.instance).trackScanSuccess("barcode")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to track scan success: ${e.message}")
            }

            val resultIntent = Intent().apply {
                putExtra(BARCODE_RESULT, barcodeValue)
            }
            setResult(RESULT_OK, resultIntent)

            // Notify gamification system about the scanned barcode so it can unlock achievements
            try {
                val userId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "default_user"
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val simplified = SimplifiedGamificationManager.getInstance(this@BarcodeScannerActivity)
                        simplified.triggerEvent(userId, SimplifiedGamificationManager.EVENT_BARCODE_SCANNED)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to post barcode event to gamification: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to post gamification event: ${e.message}")
            }

            // Cancel any existing toasts first
            val toast = Toast.makeText(this, getString(R.string.barcode_detected), Toast.LENGTH_SHORT)
            toast.show()

            // Finish immediately without delay
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::scanLineAnimator.isInitialized && !scanLineAnimator.isRunning) {
            scanLineAnimator.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::scanLineAnimator.isInitialized) {
            scanLineAnimator.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::barcodeScanner.isInitialized) {
            barcodeScanner.close()
        }

        // Libérer le gestionnaire des célébrations
        if (::achievementCelebrationManager.isInitialized) {
            achievementCelebrationManager.release()
        }
    }

    // Classe interne pour analyser les images à la recherche de codes-barres
    private inner class BarcodeAnalyzer(
        private val onBarcodesDetected: (List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            onBarcodesDetected(barcodes)
                        }
                    }
                    .addOnFailureListener { ex ->
                        Log.e(TAG, "Barcode scanning failed", ex)
                        try {
                            AnalyticsService.getInstance(ShooptApplication.instance).trackScanFailed("barcode", ex.message ?: "scan_processing_error")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to track scan failure: ${e.message}")
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
