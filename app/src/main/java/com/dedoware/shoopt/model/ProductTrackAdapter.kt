package com.dedoware.shoopt.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R

class ProductTrackAdapter(private val productList: List<CartItem>) :
    RecyclerView.Adapter<ProductTrackAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.product_image)
        val productName: TextView = itemView.findViewById(R.id.product_name)
        val productQuantity: TextView = itemView.findViewById(R.id.product_quantity)
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
    }

    override fun getItemCount(): Int {
        return productList.size
    }
}
