package com.dedoware.shoopt.utils

import android.content.Context
import android.view.*
import com.dedoware.shoopt.R

/**
 * Système de guide contextuel moderne avec spotlight et tooltips adaptatifs
 * Version améliorée avec animations fluides et UX premium
 */
class ModernGuideSystem(private val context: Context) {

    private var currentOverlay: ModernGuideOverlay? = null
    private var isGuideActive = false
    private var guideQueue = mutableListOf<GuideStep>()
    private var isProcessingQueue = false

    companion object {
        private const val PREF_FIRST_PRODUCT_ADDED = "first_product_added"
        private const val PREF_GUIDE_SHOWN = "modern_guide_shown"
        private const val ANIMATION_DURATION_SHORT = 200L

        fun isFirstProductAdded(context: Context): Boolean {
            val prefs = context.getSharedPreferences("shoopt_guide", Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_FIRST_PRODUCT_ADDED, false)
        }

        fun setFirstProductAdded(context: Context, added: Boolean) {
            val prefs = context.getSharedPreferences("shoopt_guide", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_FIRST_PRODUCT_ADDED, added).apply()
        }

        fun isGuideShown(context: Context, guideType: String): Boolean {
            val prefs = context.getSharedPreferences("shoopt_guide", Context.MODE_PRIVATE)
            return prefs.getBoolean("${PREF_GUIDE_SHOWN}_$guideType", false)
        }

        fun setGuideShown(context: Context, guideType: String, shown: Boolean) {
            val prefs = context.getSharedPreferences("shoopt_guide", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("${PREF_GUIDE_SHOWN}_$guideType", shown).apply()
        }

        fun resetAllGuides(context: Context) {
            val prefs = context.getSharedPreferences("shoopt_guide", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }

    /**
     * Configuration d'une étape du guide
     */
    data class GuideStep(
        val targetView: View? = null,
        val titleResId: Int,
        val descriptionResId: Int,
        val spotlightType: SpotlightType = SpotlightType.CIRCLE,
        val tooltipPosition: TooltipPosition = TooltipPosition.AUTO,
        val requiresUserAction: Boolean = true,
        val showConfirmButton: Boolean = false,
        val confirmButtonTextResId: Int = R.string.got_it,
        val onConfirm: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null,
        val delayBeforeShow: Long = 0L,
        val priority: Priority = Priority.NORMAL,
        val guideId: String = ""
    )

    enum class SpotlightType {
        CIRCLE,          // Cercle autour de l'élément
        ROUNDED_RECT,    // Rectangle arrondi
        NONE,           // Pas de spotlight (pour les messages centrés)
        PULSE           // Cercle avec effet de pulsation
    }

    enum class TooltipPosition {
        AUTO,           // Position automatique selon l'espace disponible
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
        CENTER          // Au centre de l'écran
    }

    enum class Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    /**
     * Affiche une étape du guide moderne avec gestion de file d'attente
     */
    fun showGuideStep(parentView: ViewGroup, step: GuideStep) {
        showGuideStep(parentView, step, forceReplay = false)
    }

    /**
     * Version avec paramètre forceReplay pour le système de replay
     */
    fun showGuideStep(parentView: ViewGroup, step: GuideStep, forceReplay: Boolean = false) {
        try {
            // Ajouter à la file d'attente si un guide est déjà actif
            if (isGuideActive) {
                guideQueue.add(step)
                sortGuideQueue()
                return
            }

            // Vérifier si le guide a déjà été montré (sauf si forceReplay = true)
            if (!forceReplay && step.guideId.isNotEmpty() && isGuideShown(context, step.guideId)) {
                processNextInQueue()
                return
            }

            // Fermer l'overlay actuel s'il existe
            dismissCurrentGuide()

            isGuideActive = true

            // Délai avant affichage si spécifié
            if (step.delayBeforeShow > 0) {
                parentView.postDelayed({
                    showGuideStepImmediate(parentView, step)
                }, step.delayBeforeShow)
            } else {
                showGuideStepImmediate(parentView, step)
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du guide moderne: ${e.message}")
            CrashlyticsManager.logException(e)
            isGuideActive = false
            processNextInQueue()
        }
    }

    /**
     * Force le replay d'un guide même s'il a déjà été montré
     */
    fun replayGuide(parentView: ViewGroup, step: GuideStep) {
        CrashlyticsManager.log("Replay du guide demandé: ${step.guideId}")
        showGuideStep(parentView, step, forceReplay = true)
    }

    /**
     * Force le replay d'un guide par son ID
     */
    fun replayGuideById(parentView: ViewGroup, guideId: String, step: GuideStep) {
        CrashlyticsManager.log("Replay du guide par ID: $guideId")
        // Temporairement réinitialiser le statut du guide pour forcer son affichage
        val wasShown = isGuideShown(context, guideId)
        if (wasShown) {
            setGuideShown(context, guideId, false)
        }

        showGuideStep(parentView, step)

        // Optionnel: remettre le statut après affichage si nécessaire
        // setGuideShown(context, guideId, true)
    }

    /**
     * Replay d'une séquence complète de guides
     */
    fun replayGuideSequence(parentView: ViewGroup, steps: List<GuideStep>) {
        CrashlyticsManager.log("Replay d'une séquence de ${steps.size} guides")

        // Vider la queue actuelle
        guideQueue.clear()

        // Fermer le guide actuel si il y en a un
        dismissCurrentGuide()

        // Ajouter tous les steps à la queue avec force replay
        steps.forEach { step ->
            guideQueue.add(step)
        }

        // Commencer par le premier
        if (guideQueue.isNotEmpty()) {
            val firstStep = guideQueue.removeAt(0)
            showGuideStep(parentView, firstStep, forceReplay = true)
        }
    }

    private fun showGuideStepImmediate(parentView: ViewGroup, step: GuideStep) {
        try {
            // Créer l'overlay moderne
            val overlay = ModernGuideOverlay(context, step) {
                dismissCurrentGuide()
                step.onDismiss?.invoke()

                // Marquer comme montré si un ID est fourni
                if (step.guideId.isNotEmpty()) {
                    setGuideShown(context, step.guideId, true)
                }

                // Traiter le prochain dans la file
                processNextInQueue()
            }

            // Ajouter l'overlay au parent
            parentView.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            currentOverlay = overlay
            overlay.show()

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage immédiat du guide: ${e.message}")
            CrashlyticsManager.logException(e)
            isGuideActive = false
            processNextInQueue()
        }
    }

    private fun sortGuideQueue() {
        guideQueue.sortByDescending { it.priority.ordinal }
    }

    private fun processNextInQueue() {
        if (isProcessingQueue || guideQueue.isEmpty()) return

        isProcessingQueue = true
        val nextStep = guideQueue.removeAt(0)

        // Délai court pour permettre une transition fluide
        currentOverlay?.let { overlay ->
            val parent = overlay.parent as? ViewGroup
            parent?.postDelayed({
                isProcessingQueue = false
                if (parent.childCount > 0) { // Vérifier que le parent existe encore
                    showGuideStep(parent, nextStep)
                }
            }, ANIMATION_DURATION_SHORT)
        } ?: run {
            isProcessingQueue = false
        }
    }

    /**
     * Ferme le guide actuel avec animation fluide
     */
    fun dismissCurrentGuide() {
        currentOverlay?.hide {
            val parent = currentOverlay?.parent as? ViewGroup
            parent?.removeView(currentOverlay)
            currentOverlay = null
            isGuideActive = false
        }
    }

    /**
     * Ferme tous les guides et vide la file d'attente
     */
    fun dismissAllGuides() {
        guideQueue.clear()
        dismissCurrentGuide()
    }

    /**
     * Guide pour le premier produit (1ère étape)
     */
    fun showFirstProductGuide(parentView: ViewGroup, addProductButton: View) {
        if (isGuideShown(context, "first_product")) return

        showGuideStep(parentView, GuideStep(
            targetView = addProductButton,
            titleResId = R.string.guide_welcome_title,
            descriptionResId = R.string.guide_welcome_description,
            spotlightType = SpotlightType.CIRCLE,
            tooltipPosition = TooltipPosition.AUTO,
            requiresUserAction = true,
            onDismiss = {
                setGuideShown(context, "first_product", true)
            }
        ))
    }

    /**
     * Guide pour l'ajout de photo (3ème étape)
     */
    fun showPhotoGuide(parentView: ViewGroup, photoButton: View) {
        if (isGuideShown(context, "photo_guide")) return

        showGuideStep(parentView, GuideStep(
            targetView = photoButton,
            titleResId = R.string.guide_photo_title,
            descriptionResId = R.string.guide_photo_description,
            spotlightType = SpotlightType.ROUNDED_RECT,
            tooltipPosition = TooltipPosition.AUTO,
            requiresUserAction = true,
            onDismiss = {
                setGuideShown(context, "photo_guide", true)
            }
        ))
    }

    /**
     * Guide de vérification (4ème étape)
     */
    fun showVerificationGuide(parentView: ViewGroup, onConfirm: () -> Unit) {
        if (isGuideShown(context, "verification_guide")) return

        showGuideStep(parentView, GuideStep(
            targetView = null, // Pas de cible spécifique
            titleResId = R.string.guide_verification_title,
            descriptionResId = R.string.guide_verification_description,
            spotlightType = SpotlightType.NONE,
            tooltipPosition = TooltipPosition.CENTER,
            requiresUserAction = true,
            showConfirmButton = true,
            confirmButtonTextResId = R.string.got_it_verified,
            onConfirm = {
                setGuideShown(context, "verification_guide", true)
                onConfirm()
            }
        ))
    }

    /**
     * Guide de sauvegarde (5ème étape)
     */
    fun showSaveGuide(parentView: ViewGroup, saveButton: View) {
        if (isGuideShown(context, "save_guide")) return

        showGuideStep(parentView, GuideStep(
            targetView = saveButton,
            titleResId = R.string.guide_save_title,
            descriptionResId = R.string.guide_save_description,
            spotlightType = SpotlightType.CIRCLE,
            tooltipPosition = TooltipPosition.AUTO,
            requiresUserAction = true,
            onDismiss = {
                setGuideShown(context, "save_guide", true)
            }
        ))
    }

    /**
     * Félicitations finales (6ème étape)
     */
    fun showFinalCongratulations(parentView: ViewGroup, analyseButton: View) {
        if (isGuideShown(context, "final_congratulations")) return

        showGuideStep(parentView, GuideStep(
            targetView = analyseButton,
            titleResId = R.string.guide_congratulations_title,
            descriptionResId = R.string.guide_congratulations_description,
            spotlightType = SpotlightType.CIRCLE,
            tooltipPosition = TooltipPosition.AUTO,
            requiresUserAction = false,
            showConfirmButton = true,
            confirmButtonTextResId = R.string.awesome_got_it,
            onConfirm = {
                setGuideShown(context, "final_congratulations", true)
                setFirstProductAdded(context, true)

                // Analytics
                AnalyticsManager.logUserAction(
                    "guide_completed",
                    "milestone",
                    mapOf("completion_time" to System.currentTimeMillis())
                )
            }
        ))
    }

    /**
     * Vérifications de guides
     */
    fun shouldShowFirstProductGuide(): Boolean = !isFirstProductAdded(context) && !isGuideShown(context, "first_product")
    fun shouldShowPhotoGuide(): Boolean = !isGuideShown(context, "photo_guide") && !isFirstProductAdded(context)
    fun shouldShowVerificationGuide(): Boolean = !isGuideShown(context, "verification_guide") && !isFirstProductAdded(context)
    fun shouldShowSaveGuide(): Boolean = !isGuideShown(context, "save_guide") && !isFirstProductAdded(context)
    fun shouldShowFinalCongratulations(): Boolean = isFirstProductAdded(context) && !isGuideShown(context, "final_congratulations")
}
