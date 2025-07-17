package com.dedoware.shoopt.helpers

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R

/**
 * Classe utilitaire pour afficher des indices visuels sur les gestes de swipe
 * disponibles dans les RecyclerViews
 */
object KotlinSwipeHintHelper {

    private const val HINT_DELAY_MS = 800L
    private const val PREF_SWIPE_HINT_SHOWN = "swipe_hint_shown"

    /**
     * Affiche une animation de swipe sur le premier élément visible du RecyclerView
     * pour indiquer à l'utilisateur qu'il peut swiper pour supprimer
     */
    fun showSwipeHint(recyclerView: RecyclerView) {
        // Attendre que la liste ait fini de se charger
        recyclerView.post {
            // S'assurer qu'il y a au moins un élément dans la liste
            if (recyclerView.adapter == null || recyclerView.adapter?.itemCount == 0) {
                return@post
            }

            // Récupérer le premier élément visible
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(0) ?: return@post
            val itemView = viewHolder.itemView
            val foregroundView = itemView.findViewById<View>(R.id.product_item_foreground) ?: return@post

            // Créer une animation qui déplace légèrement l'élément vers la gauche puis revient
            val swipeAnimation = AnimationUtils.loadAnimation(
                    recyclerView.context, R.anim.swipe_hint_animation)

            // Exécuter l'animation après un court délai
            itemView.postDelayed({
                foregroundView.startAnimation(swipeAnimation)
            }, HINT_DELAY_MS)
        }
    }

    /**
     * Pour une version plus sophistiquée : animation qui révèle partiellement l'arrière-plan
     * "Supprimer" puis revient à sa position initiale
     */
    fun showAdvancedSwipeHint(recyclerView: RecyclerView) {
        recyclerView.post {
            if (recyclerView.adapter == null || recyclerView.adapter?.itemCount == 0) {
                return@post
            }

            val viewHolder = recyclerView.findViewHolderForAdapterPosition(0) ?: return@post
            val itemView = viewHolder.itemView
            val foregroundView = itemView.findViewById<View>(R.id.product_item_foreground) ?: return@post

            itemView.postDelayed({
                // Déplacer légèrement l'élément vers la gauche pour révéler l'option de suppression
                val animator = ObjectAnimator.ofFloat(
                        foregroundView, "translationX", 0f, -200f)
                animator.duration = 800

                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Revenir à la position d'origine après une pause
                        foregroundView.postDelayed({
                            ObjectAnimator.ofFloat(foregroundView, "translationX", -200f, 0f)
                                    .apply {
                                        duration = 600
                                        start()
                                    }
                        }, 500)
                    }
                })

                animator.start()
            }, HINT_DELAY_MS)
        }
    }
}
