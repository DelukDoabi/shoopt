package com.dedoware.shoopt.model

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R

class ProductTrackAdapter(private val productList: MutableList<CartItem>) :
    RecyclerView.Adapter<ProductTrackAdapter.ProductViewHolder>() {

    // Indique si l'indice de swipe a déjà été montré
    private var hasShownSwipeHint = false

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.product_image)
        val productName: TextView = itemView.findViewById(R.id.product_name)
        val productQuantity: TextView = itemView.findViewById(R.id.product_quantity)
        val foregroundView: View? = itemView.findViewById(R.id.product_item_foreground)
        val backgroundView: View? = itemView.findViewById(R.id.swipe_background)
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
