package com.dedoware.shoopt.model

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.utils.CrashlyticsManager

class ProductTrackAdapter(private val productList: MutableList<CartItem>) :
    RecyclerView.Adapter<ProductTrackAdapter.ProductViewHolder>() {

    // Indique si l'indice de swipe a déjà été montré
    private var hasShownSwipeHint = false

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.product_image)
        val productName: TextView = itemView.findViewById(R.id.product_name)
        val productQuantity: TextView = itemView.findViewById(R.id.product_quantity)
        val foregroundView: View? = itemView.findViewById(R.id.product_item_foreground)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.product_track, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val productItem = productList[position]
        val product = productItem.product

        Glide.with(holder.productImage.context).load(product.pictureUrl).into(holder.productImage)

        holder.productName.text = product.name
        holder.productQuantity.text = productItem.quantity.toString()

        // Afficher un indicateur visuel de swipe uniquement sur le premier élément
        if (position == 0 && !hasShownSwipeHint) {
            holder.foregroundView?.let { foregroundView ->
                // Réinitialiser tout fond existant
                foregroundView.setBackgroundColor(0xFFF5F5F5.toInt())

                // Charger et démarrer l'animation de swipe
                val animator = AnimatorInflater.loadAnimator(
                    foregroundView.context,
                    R.animator.swipe_gesture_hint
                )
                animator.setTarget(foregroundView)

                // Léger délai avant de démarrer l'animation pour laisser le temps à l'utilisateur de voir l'écran
                foregroundView.postDelayed({
                    animator.start()
                    // Marquer l'indice comme montré après la fin de l'animation
                    animator.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            hasShownSwipeHint = true
                        }
                    })
                }, 1000)
            }
        } else {
            // S'assurer que les autres éléments ont un fond normal
            holder.foregroundView?.setBackgroundColor(0xFFF5F5F5.toInt())
        }

        // Ajouter un écouteur de clic pour marquer l'élément comme acheté / coché
        holder.itemView.setOnClickListener {
            try {
                val key = if (product.id.isNotBlank()) product.id else product.name
                val currentChecked = (holder.itemView.getTag(R.id.product_name) as? Boolean) ?: false
                val newChecked = !currentChecked
                holder.itemView.setTag(R.id.product_name, newChecked)

                // Mise à jour visuelle : barrer le nom si coché
                if (newChecked) {
                    holder.productName.paintFlags = holder.productName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    holder.productName.alpha = 0.6f
                } else {
                    holder.productName.paintFlags = holder.productName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    holder.productName.alpha = 1.0f
                }

                // Tracking analytics
                try {
                    AnalyticsService.getInstance(holder.itemView.context).trackPurchaseItemCheck(
                        key,
                        product.name,
                        newChecked
                    )
                } catch (e: Exception) {
                    // Fallback : log raw event
                    try {
                        val bundle = android.os.Bundle().apply {
                            putString("product_id", key)
                            putString("product_name", product.name)
                            putBoolean("is_checked", newChecked)
                        }
                        AnalyticsService.getInstance(holder.itemView.context).logEvent("purchase_tracking_item_check", bundle)
                    } catch (_: Exception) {
                        // ignore
                    }
                    CrashlyticsManager.log("Erreur lors du tracking d'un produit coché: ${e.message ?: "Message non disponible"}")
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du clic sur un produit: ${e.message ?: "Message non disponible"}")
            }
        }
    }

    override fun getItemCount(): Int {
        return productList.size
    }

    fun removeAt(position: Int) {
        productList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun getItems(): List<CartItem> {
        return productList
    }

    fun updateItems(newItems: List<CartItem>) {
        productList.clear()
        productList.addAll(newItems)
        notifyDataSetChanged()
    }

    /**
     * Indique si l'animation d'indice de swipe a déjà été montrée
     */
    fun hasShownSwipeHint(): Boolean = hasShownSwipeHint

    /**
     * Marque l'indice de swipe comme ayant été montré
     */
    fun setSwipeHintShown() {
        hasShownSwipeHint = true
    }
}
