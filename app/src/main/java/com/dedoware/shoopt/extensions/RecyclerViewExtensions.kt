package com.dedoware.shoopt.extensions

import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.helpers.KotlinSwipeHintHelper
import com.dedoware.shoopt.model.ProductTrackAdapter

/**
 * Méthodes d'extension pour faciliter l'intégration des fonctionnalités de swipe
 * dans les RecyclerViews
 */

/**
 * Configure un RecyclerView pour permettre la suppression par swipe et
 * montre une animation d'indication au premier chargement
 */
fun RecyclerView.setupSwipeToDelete(onItemSwiped: (Int) -> Unit, showInitialHint: Boolean = true) {
    // Configuration du swipe pour supprimer
    val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        0, ItemTouchHelper.LEFT) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                          target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            onItemSwiped(position)
        }
    })

    // Attacher l'helper au RecyclerView
    itemTouchHelper.attachToRecyclerView(this)

    // Montrer l'animation d'indication si demandé et s'il y a des éléments
    if (showInitialHint) {
        this.post {
            if (this.adapter != null && this.adapter?.itemCount ?: 0 > 0) {
                KotlinSwipeHintHelper.showAdvancedSwipeHint(this)
            }
        }
    }
}

/**
 * Montre explicitement l'animation d'indication de swipe
 */
fun RecyclerView.showSwipeHint() {
    if (this.adapter != null && this.adapter?.itemCount ?: 0 > 0) {
        KotlinSwipeHintHelper.showAdvancedSwipeHint(this)
    }
}
