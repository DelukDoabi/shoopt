package com.dedoware.shoopt.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestionnaire des effets sonores pour améliorer l'expérience utilisateur
 */
class CelebrationSoundManager private constructor(context: Context) {

    private var soundPool: SoundPool? = null
    private var successSoundId: Int = 0
    private var isLoaded = false
    private val appContext = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: CelebrationSoundManager? = null

        fun getInstance(context: Context): CelebrationSoundManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CelebrationSoundManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        initializeSoundPool()
    }

    private fun initializeSoundPool() {
        try {
            soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(audioAttributes)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                SoundPool(2, AudioManager.STREAM_MUSIC, 0)
            }

            soundPool?.setOnLoadCompleteListener { _, _, status ->
                isLoaded = (status == 0)
            }

            // Charger le son de succès (on utilisera un son système si pas de fichier custom)
            // Pour l'instant, on utilisera un son généré programmatiquement
        } catch (e: Exception) {
            // Ignore si l'audio n'est pas disponible
        }
    }

    /**
     * Joue le son de célébration pour le premier produit
     */
    fun playCelebrationSound() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // On peut soit utiliser un son système ou créer des bips programmés
                playSystemSuccessSound()
            } catch (e: Exception) {
                // Ignore si le son ne peut pas être joué
            }
        }
    }

    /**
     * Joue le son de succès - alias pour playCelebrationSound
     */
    fun playSuccessSound() {
        playCelebrationSound()
    }

    private suspend fun playSystemSuccessSound() {
        // Séquence de sons de notification pour créer un effet de célébration
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            // Jouer une séquence de sons de notification rapides
            repeat(3) { index ->
                try {
                    // Utiliser les sons système disponibles
                    val notification = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION
                    )

                    val ringtone = android.media.RingtoneManager.getRingtone(appContext, notification)
                    ringtone?.play()

                    delay(200) // Pause entre les sons

                    ringtone?.stop()
                } catch (e: Exception) {
                    // Continue même si un son échoue
                }

                if (index < 2) delay(100)
            }
        }
    }

    /**
     * Vérifie si les sons sont activés dans les paramètres du système
     */
    fun isSoundEnabled(): Boolean {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    /**
     * Libère les ressources
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}
