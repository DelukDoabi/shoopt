package com.dedoware.shoopt.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dedoware.shoopt.R
import com.dedoware.shoopt.utils.CurrencyManager
import com.dedoware.shoopt.utils.UserPreferences
import com.dedoware.shoopt.utils.convertAndFormatAsPrice
import com.dedoware.shoopt.utils.getCurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ProductListAdapter(
    private val products: List<Product>,
    private val userPreferences: UserPreferences
) : RecyclerView.Adapter<ProductListAdapter.ViewHolder>() {

    private var onItemClickListener: ((Product) -> Unit)? = null
    private var onItemLongClickListener: ((Product) -> Unit)? = null
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    // Devise d'origine des produits (supposée être EUR pour les données existantes)
    private val originalCurrency = "EUR"

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.product_name_TV)
        private val shopTextView: TextView = itemView.findViewById(R.id.product_shop_TV)
        private val priceTextView: TextView = itemView.findViewById(R.id.product_full_price_TV)
        private val dateTextView: TextView = itemView.findViewById(R.id.product_last_update_date_TV)
        private val imageView: ImageView = itemView.findViewById(R.id.product_image_IV)

        fun bind(product: Product) {
            nameTextView.text = product.name
            shopTextView.text = product.shop
            dateTextView.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(product.timestamp))

            // Convertir le prix du produit avec la devise actuelle
            convertAndDisplayPrice(product)

            Glide.with(itemView.context).load(product.pictureUrl).into(imageView)

            itemView.setOnClickListener {
                onItemClickListener?.invoke(product)
            }

            itemView.setOnLongClickListener {
                onItemLongClickListener?.invoke(product)
                true
            }
        }

        private fun convertAndDisplayPrice(product: Product) {
            val context = itemView.context
            val currencyManager = context.getCurrencyManager()
            val currentCurrency = currencyManager.getCurrentCurrencyCode()

            // Afficher un message temporaire pendant la conversion
            priceTextView.text = "Conversion en cours..."

            // Convertir le prix et le prix unitaire
            adapterScope.launch {
                try {
                    // Convertir le prix du produit
                    val convertedPrice = currencyManager.convertToCurrentCurrencySuspend(product.price, originalCurrency)
                    val convertedUnitPrice = currencyManager.convertToCurrentCurrencySuspend(product.unitPrice, originalCurrency)

                    // Formater les prix convertis
                    val formattedPrice = currencyManager.formatPrice(convertedPrice)
                    val formattedUnitPrice = currencyManager.formatPrice(convertedUnitPrice)

                    // Afficher le résultat
                    withContext(Dispatchers.Main) {
                        priceTextView.text = "$formattedPrice ($formattedUnitPrice)"
                    }
                } catch (e: Exception) {
                    // En cas d'erreur, utiliser le formatage simple
                    withContext(Dispatchers.Main) {
                        val simplePrice = currencyManager.formatPrice(product.price)
                        val simpleUnitPrice = currencyManager.formatPrice(product.unitPrice)
                        priceTextView.text = "$simplePrice ($simpleUnitPrice)"
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.product, parent, false)
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