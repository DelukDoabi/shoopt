package com.dedoware.shoopt.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.UserPreferences
import java.text.SimpleDateFormat
import java.util.*

class ProductListAdapter(
    private val products: List<Product>,
    private val userPreferences: UserPreferences
) : RecyclerView.Adapter<ProductListAdapter.ViewHolder>() {

    private var onItemClickListener: ((Product) -> Unit)? = null
    private var onItemLongClickListener: ((Product) -> Unit)? = null

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(product: Product) {
            itemView.findViewById<TextView>(R.id.product_name_TV).text = product.name
            itemView.findViewById<TextView>(R.id.product_shop_TV).text = product.shop
            itemView.findViewById<TextView>(R.id.product_full_price_TV).text =
                userPreferences.formatPrice(product.price) + " (" + userPreferences.formatPrice(product.unitPrice) + ")"
            itemView.findViewById<TextView>(R.id.product_last_update_date_TV).text =
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(product.timestamp))

            val imageView = itemView.findViewById<ImageView>(R.id.product_image_IV)
            Glide.with(itemView.context).load(product.pictureUrl).into(imageView)

            itemView.setOnClickListener {
                onItemClickListener?.invoke(product)
            }

            itemView.setOnLongClickListener {
                onItemLongClickListener?.invoke(product)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.product, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return products.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(products[position])
    }

    fun setOnItemClickListener(listener: (Product) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnLongClickListener(listener: (Product) -> Unit) {
        onItemLongClickListener = listener
    }
}