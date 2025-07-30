package com.dedoware.shoopt.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.dedoware.shoopt.R
import com.google.android.material.button.MaterialButton

class ContextualGuideManager(private val context: Context) {

    private var currentBubble: View? = null
    private var isGuideActive = false

    companion object {
        private const val PREF_FIRST_PRODUCT_ADDED = "first_product_added"
        private const val PREF_GUIDE_SHOWN = "contextual_guide_shown"

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
    }

    /**
     * Ferme manuellement la bulle actuelle (à appeler quand l'action demandée est accomplie)
     */
    fun dismissCurrentBubble() {
        currentBubble?.let { bubble ->
            val parentView = bubble.parent as? ViewGroup
            if (parentView != null) {
                dismissHelpBubble(bubble, parentView, null, null)
            }
        }
    }

    /**
     * Affiche une bulle d'aide avec la mascotte
     */
    fun showHelpBubble(
        parentView: ViewGroup,
        targetView: View? = null,
        messageResId: Int,
        subtitleResId: Int? = null,
        position: BubblePosition = BubblePosition.ABOVE,
        onDismiss: (() -> Unit)? = null,
        autoHideAfter: Long = 0,
        onBubbleClick: (() -> Unit)? = null, // Action à exécuter quand on clique sur la bulle
        requiresUserAction: Boolean = true, // Nouvelle option pour indiquer si une action utilisateur est requise
        useOverlay: Boolean = false, // Nouvelle option pour ajouter une overlay semi-transparente
        showConfirmButton: Boolean = false, // Nouvelle option pour afficher un bouton de confirmation
        confirmButtonTextResId: Int = R.string.got_it, // Texte du bouton de confirmation
        onConfirm: (() -> Unit)? = null // Action à exécuter quand on confirme
    ) {
        // Fermer la bulle actuelle s'il y en a une avant d'en créer une nouvelle
        currentBubble?.let { bubble ->
            (bubble.parent as? ViewGroup)?.removeView(bubble)
            currentBubble = null
        }

        if (isGuideActive) {
            isGuideActive = false // Reset pour permettre une nouvelle bulle
        }

        try {
            // Marquer le guide comme actif
            isGuideActive = true

            // Créer une overlay semi-transparente si demandée
            var overlayView: View? = null
            if (useOverlay) {
                overlayView = View(context).apply {
                    setBackgroundColor(0x80000000.toInt()) // Noir semi-transparent
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    elevation = 100f // Très haute élévation
                }
                parentView.addView(overlayView)
            }

            // Créer la bulle d'aide
            val bubbleView = LayoutInflater.from(context)
                .inflate(R.layout.help_bubble_layout, parentView, false)

            // Configurer le contenu
            val helpText = bubbleView.findViewById<TextView>(R.id.help_bubble_text)
            val helpSubtitle = bubbleView.findViewById<TextView>(R.id.help_bubble_subtitle)
            val mascotImage = bubbleView.findViewById<ImageView>(R.id.mascot_image)
            val closeButton = bubbleView.findViewById<ImageButton>(R.id.help_bubble_close)
            val arrow = bubbleView.findViewById<ImageView>(R.id.help_bubble_arrow)
            val arrowUp = bubbleView.findViewById<ImageView>(R.id.help_bubble_arrow_up)
            val confirmButton = bubbleView.findViewById<MaterialButton>(R.id.help_bubble_confirm)

            helpText.setText(messageResId)

            if (subtitleResId != null) {
                helpSubtitle.setText(subtitleResId)
                helpSubtitle.visibility = View.VISIBLE
            } else {
                helpSubtitle.visibility = View.GONE
            }

            // Gérer le bouton de confirmation
            if (showConfirmButton) {
                confirmButton.visibility = View.VISIBLE
                confirmButton.setText(confirmButtonTextResId)
                closeButton.visibility = View.GONE // Masquer le bouton de fermeture classique

                confirmButton.setOnClickListener {
                    onConfirm?.invoke()
                    dismissHelpBubble(bubbleView, parentView, onDismiss, overlayView)
                }
            } else {
                confirmButton.visibility = View.GONE
                closeButton.visibility = View.VISIBLE
            }

            // Position de la bulle
            positionBubble(bubbleView, parentView, targetView, position, arrow, arrowUp)

            // Animation d'entrée de la mascotte
            animateMascot(mascotImage)

            // Gestionnaire de fermeture modifié pour supprimer aussi l'overlay
            val dismissBubble = {
                dismissHelpBubble(bubbleView, parentView, onDismiss, overlayView)
            }

            closeButton.setOnClickListener { dismissBubble() }

            // Gestionnaire de clic sur la bulle - exécute l'action mais ne ferme la bulle que si requiresUserAction est false
            if (onBubbleClick != null) {
                bubbleView.setOnClickListener {
                    onBubbleClick.invoke()
                    // Ne fermer la bulle automatiquement que si aucune action utilisateur spécifique n'est requise
                    if (!requiresUserAction) {
                        dismissBubble()
                    }
                }
            }

            // Ajouter la bulle au parent
            parentView.addView(bubbleView)
            currentBubble = bubbleView

            // Ajuster la position pour s'assurer que la bulle est au premier plan
            bubbleView.elevation = if (useOverlay) 300f else 200f // Élévation plus haute avec overlay
            bubbleView.bringToFront()

            // Animation d'entrée
            showBubbleWithAnimation(bubbleView)

            // Auto-hide seulement si autoHideAfter > 0 ET qu'aucune action utilisateur n'est requise
            if (autoHideAfter > 0 && !requiresUserAction && !showConfirmButton) {
                bubbleView.postDelayed(dismissBubble, autoHideAfter)
            }

            // Analytics
            AnalyticsManager.logUserAction(
                "help_bubble_shown",
                "guide",
                mapOf("message_id" to context.resources.getResourceEntryName(messageResId))
            )

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage de la bulle d'aide: ${e.message}")
            CrashlyticsManager.logException(e)
            isGuideActive = false
        }
    }

    private fun positionBubble(
        bubbleView: View,
        parentView: ViewGroup,
        targetView: View?,
        position: BubblePosition,
        arrow: ImageView,
        arrowUp: ImageView // Nouveau paramètre pour la flèche vers le haut
    ) {
        val layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )

        when (position) {
            BubblePosition.ABOVE -> {
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.setMargins(0, 100, 0, 0)
                arrow.visibility = View.VISIBLE
                arrow.rotation = 0f // Flèche vers le bas
                arrowUp.visibility = View.GONE // Masquer la flèche vers le haut
            }
            BubblePosition.BELOW -> {
                // Position sous la cible avec marge de sécurité pour éviter la barre système
                if (targetView != null) {
                    val location = IntArray(2)
                    targetView.getLocationInWindow(location)
                    val targetBottom = location[1] + targetView.height
                    val marginTop = targetBottom + 20 // 20dp de marge sous la cible
                    layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    layoutParams.setMargins(0, marginTop, 0, 150) // Marge bottom pour éviter la barre système

                    // Utiliser la flèche vers le haut pour pointer vers le bouton photo
                    arrow.visibility = View.GONE // Masquer la flèche normale
                    arrowUp.visibility = View.VISIBLE // Afficher la flèche vers le haut

                    // Position la flèche vers le haut pour pointer vers le centre du bouton cible
                    bubbleView.post {
                        positionArrowTowardsTarget(bubbleView, targetView, arrowUp, true) // true = pointer vers le haut
                    }
                } else {
                    layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    layoutParams.setMargins(0, 300, 0, 150)
                    arrow.visibility = View.VISIBLE
                    arrowUp.visibility = View.GONE
                }
            }
            BubblePosition.CENTER -> {
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.setMargins(0, 100, 0, 100)
                arrow.visibility = View.GONE
                arrowUp.visibility = View.GONE // Masquer la flèche vers le haut
            }
            BubblePosition.CHOICE_DIALOG -> {
                // Position spéciale pour être au-dessus des dialogues
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.setMargins(0, 200, 0, 0)
                arrow.visibility = View.GONE
                arrowUp.visibility = View.GONE // Masquer la flèche vers le haut
            }
        }

        bubbleView.layoutParams = layoutParams
    }

    /**
     * Positionne la flèche pour qu'elle pointe précisément vers le centre du bouton cible
     */
    private fun positionArrowTowardsTarget(bubbleView: View, targetView: View, arrow: ImageView, pointUp: Boolean = false) {
        try {
            // Obtenir les positions des vues
            val bubbleLocation = IntArray(2)
            val targetLocation = IntArray(2)

            bubbleView.getLocationInWindow(bubbleLocation)
            targetView.getLocationInWindow(targetLocation)

            // Calculer le centre horizontal du bouton cible
            val targetCenterX = targetLocation[0] + (targetView.width / 2)

            // Calculer le centre horizontal de la bulle
            val bubbleCenterX = bubbleLocation[0] + (bubbleView.width / 2)

            // Calculer l'offset nécessaire pour centrer la flèche sur le bouton
            val offsetX = targetCenterX - bubbleCenterX

            // Appliquer l'offset à la flèche (avec une limite pour éviter que la flèche sorte de la bulle)
            val maxOffset = (bubbleView.width / 2) - (arrow.width / 2) - 32 // 32dp de marge
            val constrainedOffset = offsetX.coerceIn(-maxOffset, maxOffset)

            // Obtenir les paramètres de layout actuels de la flèche
            val arrowParams = arrow.layoutParams as ConstraintLayout.LayoutParams

            // Supprimer les contraintes de centrage existantes
            arrowParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            arrowParams.endToEnd = ConstraintLayout.LayoutParams.UNSET

            // Définir une nouvelle position horizontale
            if (constrainedOffset >= 0) {
                arrowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                arrowParams.marginStart = (bubbleView.width / 2) + constrainedOffset - (arrow.width / 2)
            } else {
                arrowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                arrowParams.marginEnd = (bubbleView.width / 2) - constrainedOffset - (arrow.width / 2)
            }

            // Rotation pour pointer vers le haut si demandé
            if (pointUp) {
                arrow.rotation = 180f // Pointer vers le haut
            } else {
                arrow.rotation = 0f // Pointer vers le bas par défaut
            }

            arrow.layoutParams = arrowParams

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du positionnement de la flèche: ${e.message}")
            // En cas d'erreur, garder la flèche centrée par défaut
        }
    }

    private fun animateMascot(mascotImage: ImageView) {
        // Animation de "respiration" pour la mascotte
        val scaleUp = ObjectAnimator.ofFloat(mascotImage, "scaleX", 1f, 1.1f)
        val scaleUpY = ObjectAnimator.ofFloat(mascotImage, "scaleY", 1f, 1.1f)
        val scaleDown = ObjectAnimator.ofFloat(mascotImage, "scaleX", 1.1f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(mascotImage, "scaleY", 1.1f, 1f)

        val breatheIn = AnimatorSet().apply {
            playTogether(scaleUp, scaleUpY)
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }

        val breatheOut = AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY)
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }

        val breathingAnimation = AnimatorSet().apply {
            playSequentially(breatheIn, breatheOut)
        }

        // Répéter l'animation
        breathingAnimation.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (currentBubble != null) {
                    breathingAnimation.start()
                }
            }
        })

        breathingAnimation.start()
    }

    private fun showBubbleWithAnimation(bubbleView: View) {
        bubbleView.alpha = 0f
        bubbleView.translationY = -50f

        bubbleView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun dismissHelpBubble(
        bubbleView: View,
        parentView: ViewGroup,
        onDismiss: (() -> Unit)?,
        overlayView: View? = null // Paramètre pour l'overlay
    ) {
        bubbleView.animate()
            .alpha(0f)
            .translationY(-50f)
            .setDuration(200)
            .withEndAction {
                parentView.removeView(bubbleView)
                currentBubble = null
                isGuideActive = false
                onDismiss?.invoke()

                // Supprimer l'overlay si présent
                overlayView?.let {
                    parentView.removeView(it)
                }
            }
            .start()
    }

    /**
     * Vérifie si l'utilisateur a besoin du guide pour le premier produit
     */
    fun shouldShowFirstProductGuide(): Boolean {
        return !isFirstProductAdded(context) && !isGuideShown(context, "first_product")
    }

    /**
     * Marque le guide comme affiché
     */
    fun markGuideAsShown(guideType: String) {
        setGuideShown(context, guideType, true)
    }

    /**
     * Démarre le guide pour le premier produit
     */
    fun startFirstProductGuide(parentView: ViewGroup, addProductButton: View) {
        if (!shouldShowFirstProductGuide()) return

        showHelpBubble(
            parentView = parentView,
            targetView = addProductButton,
            messageResId = R.string.help_bubble_add_first_product,
            subtitleResId = R.string.help_bubble_subtitle,
            position = BubblePosition.ABOVE,
            autoHideAfter = 0, // Pas d'auto-hide, attendre l'action utilisateur
            requiresUserAction = true, // L'utilisateur doit cliquer sur "Ajouter un produit"
            onDismiss = {
                markGuideAsShown("first_product")
            },
            onBubbleClick = {
                // Quand l'utilisateur clique sur la bulle, simuler un clic sur le bouton d'ajout de produit
                addProductButton.performClick()
                // NE PAS marquer comme affiché ici - seulement quand le produit est effectivement ajouté
            }
        )
    }

    /**
     * Affiche la félicitation pour le premier produit
     */
    fun showFirstProductCongratulations(parentView: ViewGroup) {
        setFirstProductAdded(context, true)

        showHelpBubble(
            parentView = parentView,
            messageResId = R.string.help_bubble_congratulations,
            subtitleResId = R.string.first_product_achievement,
            position = BubblePosition.CENTER,
            autoHideAfter = 5000,
            requiresUserAction = false // Les félicitations peuvent disparaître automatiquement
        )

        // Analytics pour le premier produit ajouté
        AnalyticsManager.logUserAction(
            "first_product_added",
            "milestone",
            mapOf("guide_completed" to "true")
        )
    }

    /**
     * Affiche la bulle d'aide pour la fenêtre de choix d'options avec overlay haute élévation
     */
    fun showChoiceDialogGuide(parentView: ViewGroup) {
        showHelpBubble(
            parentView = parentView,
            targetView = null,
            messageResId = R.string.help_bubble_choice_explanation,
            subtitleResId = R.string.help_bubble_choice_subtitle,
            position = BubblePosition.CHOICE_DIALOG,
            useOverlay = true, // Overlay pour être au-dessus du dialogue
            autoHideAfter = 6000,
            requiresUserAction = false
        )
    }

    /**
     * Affiche la bulle d'aide pour expliquer l'ajout de photo (3ème étape)
     */
    fun showPhotoGuide(parentView: ViewGroup, photoButton: View) {
        showHelpBubble(
            parentView = parentView,
            targetView = photoButton,
            messageResId = R.string.help_bubble_photo_guide,
            subtitleResId = R.string.help_bubble_photo_subtitle,
            position = BubblePosition.BELOW,
            requiresUserAction = true,
            useOverlay = true,
            onDismiss = {
                markGuideAsShown("photo_guide")
            }
        )
    }

    /**
     * Affiche la bulle d'aide pour vérifier les informations (4ème étape)
     */
    fun showVerifyInfoGuide(parentView: ViewGroup, onConfirm: () -> Unit) {
        showHelpBubble(
            parentView = parentView,
            targetView = null,
            messageResId = R.string.help_bubble_verify_info,
            subtitleResId = R.string.help_bubble_verify_subtitle,
            position = BubblePosition.CENTER,
            requiresUserAction = true,
            useOverlay = true,
            showConfirmButton = true,
            confirmButtonTextResId = R.string.got_it_verified,
            onConfirm = {
                markGuideAsShown("verify_info")
                onConfirm()
            }
        )
    }

    /**
     * Affiche la bulle d'aide pour le bouton de sauvegarde (5ème étape)
     */
    fun showSaveGuide(parentView: ViewGroup, saveButton: View) {
        showHelpBubble(
            parentView = parentView,
            targetView = saveButton,
            messageResId = R.string.help_bubble_save_guide,
            subtitleResId = R.string.help_bubble_save_subtitle,
            position = BubblePosition.ABOVE,
            requiresUserAction = true,
            useOverlay = true,
            onDismiss = {
                markGuideAsShown("save_guide")
            }
        )
    }

    /**
     * Affiche les félicitations finales sur l'écran principal (6ème étape)
     */
    fun showFinalCongratulations(parentView: ViewGroup, analyseButton: View) {
        showHelpBubble(
            parentView = parentView,
            targetView = analyseButton,
            messageResId = R.string.help_bubble_final_congratulations,
            subtitleResId = R.string.help_bubble_final_subtitle,
            position = BubblePosition.ABOVE,
            autoHideAfter = 8000,
            requiresUserAction = false,
            showConfirmButton = true,
            confirmButtonTextResId = R.string.awesome_got_it,
            onConfirm = {
                // Marquer le guide complet comme terminé
                markGuideAsShown("complete_guide")

                // Analytics pour la fin du guide
                AnalyticsManager.logUserAction(
                    "complete_guide_finished",
                    "milestone",
                    mapOf("guide_completed" to "true")
                )
            }
        )
    }

    /**
     * Vérifie si l'utilisateur a besoin du guide photo
     */
    fun shouldShowPhotoGuide(): Boolean {
        return !isGuideShown(context, "photo_guide") && !isFirstProductAdded(context)
    }

    /**
     * Vérifie si l'utilisateur a besoin du guide de vérification
     */
    fun shouldShowVerifyInfoGuide(): Boolean {
        return !isGuideShown(context, "verify_info") && !isFirstProductAdded(context)
    }

    /**
     * Vérifie si l'utilisateur a besoin du guide de sauvegarde
     */
    fun shouldShowSaveGuide(): Boolean {
        return !isGuideShown(context, "save_guide") && !isFirstProductAdded(context)
    }

    /**
     * Vérifie si l'utilisateur peut voir les félicitations finales
     */
    fun shouldShowFinalCongratulations(): Boolean {
        return isFirstProductAdded(context) && !isGuideShown(context, "complete_guide")
    }
}

enum class BubblePosition {
    ABOVE,
    BELOW,
    CENTER,
    CHOICE_DIALOG
}
