package com.dedoware.shoopt.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.utils.CrashlyticsManager

class ProductTrackAdapter(
    private val products: MutableList<CartItem>
) : RecyclerView.Adapter<ProductTrackAdapter.ProductTrackViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductTrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.product_track, parent, false)
        return ProductTrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductTrackViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    fun removeAt(position: Int) {
        products.removeAt(position)
        notifyItemRemoved(position)
    }

    class ProductTrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.product_name)
        private val quantityTextView: TextView = itemView.findViewById(R.id.product_quantity)

        fun bind(cartItem: CartItem) {
            nameTextView.text = cartItem.product.name
            quantityTextView.text = cartItem.quantity.toString()

            val isChecked = (itemView.getTag(R.id.product_name) as? Boolean) ?: false
            updateVisualCheckedState(isChecked)

            // Listener de clic pour basculer l'état "acheté" - on gère un tag local sur la vue
            itemView.setOnClickListener {
                val newChecked = !(itemView.getTag(R.id.product_name) as? Boolean ?: false)
                itemView.setTag(R.id.product_name, newChecked)
                updateVisualCheckedState(newChecked)

                // Appel analytics
                try {
                    AnalyticsService.getInstance(itemView.context).trackPurchaseItemCheck(
                        cartItem.product.id.ifEmpty { cartItem.product.name },
                        cartItem.product.name,
                        newChecked
                    )
                } catch (e: Exception) {
                    // Fallback: log brut dans Analytics et enregistrer l'exception
                    try {
                        val bundle = android.os.Bundle().apply {
                            putString("product_id", cartItem.product.id.ifEmpty { cartItem.product.name })
                            putString("product_name", cartItem.product.name)
                            putBoolean("is_checked", newChecked)
                        }
                        AnalyticsService.getInstance(itemView.context).logEvent("purchase_tracking_item_check", bundle)
                    } catch (_: Exception) {
                        // nothing
                    }
                    CrashlyticsManager.log("Erreur lors du tracking d'un achat: ${e.message ?: "Message non disponible"}")
                }
            }
        }

        private fun updateVisualCheckedState(checked: Boolean) {
            if (checked) {
                nameTextView.paintFlags = nameTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                nameTextView.alpha = 0.6f
            } else {
                nameTextView.paintFlags = nameTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                nameTextView.alpha = 1.0f
            }
        }
    }
}
