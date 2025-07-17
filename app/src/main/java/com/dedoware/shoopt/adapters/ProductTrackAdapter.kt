package com.dedoware.shoopt.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.model.CartItem

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
        }
    }
}
