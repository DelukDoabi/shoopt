package com.dedoware.shoopt.gamification.manager

import android.app.Activity
import android.content.Context
import com.dedoware.shoopt.gamification.models.Achievement
import com.dedoware.shoopt.ui.dialogs.AchievementCongratulationDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Gestionnaire qui affiche les célébrations pour les achievements débloqués
 */
class AchievementCelebrationManager(private val context: Context) :
    SimplifiedGamificationManager.AchievementUnlockedListener {

    private val gamificationManager = SimplifiedGamificationManager.getInstance(context)
    private val pendingAchievements = mutableListOf<Achievement>()
    private var isShowingDialog = false

    init {
        // S'enregistrer pour recevoir les notifications d'achievements débloqués
        gamificationManager.addAchievementUnlockedListener(this)
    }

    /**
     * Appelé quand un achievement est débloqué
     */
    override suspend fun onAchievementUnlocked(userId: String, achievement: Achievement) {
        // Ajouter l'achievement à la liste d'attente
        synchronized(pendingAchievements) {
            pendingAchievements.add(achievement)
        }

        // Lancer l'affichage sur le thread principal
        CoroutineScope(Dispatchers.Main).launch {
            showNextAchievementIfPossible()
        }
    }

    /**
     * Affiche le prochain achievement en attente si possible
     */
    private fun showNextAchievementIfPossible() {
        // Si une boîte de dialogue est déjà affichée ou s'il n'y a pas d'achievement en attente, ne rien faire
        if (isShowingDialog || pendingAchievements.isEmpty() || context !is Activity) {
            return
        }

        val activity = context as Activity
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        // Récupérer le prochain achievement à afficher
        val nextAchievement: Achievement
        synchronized(pendingAchievements) {
            nextAchievement = pendingAchievements.removeAt(0)
        }

        // Marquer qu'une boîte de dialogue est en cours d'affichage
        isShowingDialog = true

        // Créer et afficher la boîte de dialogue
        val dialog = AchievementCongratulationDialog(
            context,
            nextAchievement,
            onDismissCallback = {
                // Quand la boîte de dialogue est fermée, marquer qu'aucune boîte de dialogue n'est affichée
                // et afficher la prochaine si disponible
                isShowingDialog = false
                showNextAchievementIfPossible()
            }
        )

        dialog.show()
    }

    /**
     * Libère les ressources lors de la destruction
     */
    fun release() {
        gamificationManager.removeAchievementUnlockedListener(this)
    }
}
