package com.dedoware.shoopt.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductTrackAdapter
import com.dedoware.shoopt.model.ShoppingCart
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import com.dedoware.shoopt.persistence.LocalProductRepository
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TrackShoppingActivity : ComponentActivity() {

    private lateinit var addProductImageButton: ImageButton
    private lateinit var clearCartImageButton: ImageButton
    private lateinit var backImageButton: ImageButton

    // Repository instance
    private lateinit var productRepository: IProductRepository

    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }

    private val useFirebase = false // This could be a config or user preference
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_shopping)

        productRepository = if (useFirebase) {
            FirebaseProductRepository()
        } else {
            LocalProductRepository(
                database.productDao(),
                database.shopDao(),
                database.shoppingCartDao(),
                database.cartItemDao()
            )
        }

        setupActionButtons()

        loadShoppingCart()
    }

    private fun setupActionButtons() {
        addProductImageButton = findViewById(R.id.add_product_IB)
        clearCartImageButton = findViewById(R.id.empty_cart_IB)
        backImageButton = findViewById(R.id.back_IB)

        addProductImageButton.setOnClickListener {
            displayAddProductWayUserChoice()
        }

        clearCartImageButton.setOnClickListener {
            showClearCartConfirmationDialog()
        }

        backImageButton.setOnClickListener {
            finish()
        }
    }

    private fun loadShoppingCart() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val cart = productRepository.getShoppingCart()
                updateCartUI(cart)
            } catch (e: Exception) {
                showToast(getString(R.string.failed_to_load_shopping_cart))
            }
        }
    }

    private fun displayAddProductWayUserChoice() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.choose_option))
        builder.setMessage(getString(R.string.scan_barcode_or_add_manually))

        builder.setPositiveButton(getString(R.string.scan_barcode)) { _, _ ->
            barcodeLauncher.launch(ScanOptions())
        }

        builder.setNegativeButton(getString(R.string.add_product_manually)) { _, _ ->
            val addProductIntent = Intent(this, AddProductActivity::class.java)
            addProductIntent.putExtra("source", "TrackShoppingActivity")
            addProductContract.launch(addProductIntent)
        }

        val dialog = builder.create()
        dialog.show()
    }

    private val addProductContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val product = data?.getParcelableExtra<Product>("productToAddToShoppingCart")
                if (product != null) {
                    GlobalScope.launch(Dispatchers.Main) {
                        val success = productRepository.addProductToCart(product)
                        if (success) {
                            loadShoppingCart()  // Reload cart after adding the product
                            showToast(getString(R.string.product_added_to_cart))
                        } else {
                            showToast(getString(R.string.failed_to_add_product_to_cart))
                        }
                    }
                }
            }
        }

    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            showToast(getString(R.string.cancelled))
        } else {
            GlobalScope.launch(Dispatchers.Main) {
                val product = productRepository.getProductByBarcode(result.contents.toLong())
                if (product != null) {
                    val success = productRepository.addProductToCart(product)
                    if (success) {
                        loadShoppingCart()  // Reload cart after adding the product
                        showToast(getString(R.string.product_added_to_cart))
                    } else {
                        showToast(getString(R.string.failed_to_add_product_to_cart))
                    }
                } else {
                    val addProductIntent = Intent(this@TrackShoppingActivity, AddProductActivity::class.java)
                    addProductIntent.putExtra("source", "TrackShoppingActivity")
                    addProductIntent.putExtra("barcode", result.contents)
                    addProductContract.launch(addProductIntent)
                }
            }
        }
    }

    private fun updateCartUI(cart: ShoppingCart?) {
        val itemCountTextView = findViewById<TextView>(R.id.cart_item_count)
        val totalPriceTextView = findViewById<TextView>(R.id.total_price)

        val itemQuantity = cart?.products?.sumOf { it.quantity } ?: 0
        val totalPrice = cart?.products?.sumOf { it.product.price * it.quantity } ?: 0.0

        itemCountTextView.text = itemQuantity.toString()
        totalPriceTextView.text = String.format("%.2f", totalPrice)

        populateProductList(cart?.products ?: emptyList())
    }

    private fun showToast(messageToDisplay: String) {
        Toast.makeText(this, messageToDisplay, Toast.LENGTH_LONG).show()
    }

    private fun populateProductList(products: List<CartItem>) {
        val recyclerView: RecyclerView = findViewById(R.id.product_list_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ProductTrackAdapter(products)
    }

    private fun showClearCartConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.clear_shopping_cart))
        builder.setMessage(getString(R.string.clear_shopping_cart_confirm))

        builder.setPositiveButton(getString(R.string.clear)) { _, _ ->
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    val success = productRepository.clearShoppingCart()
                    if (success) {
                        loadShoppingCart()  // Reload cart after clearing
                        showToast(getString(R.string.shopping_cart_emptied))
                    } else {
                        showToast(getString(R.string.failed_to_empty_shopping_cart))
                    }
                } catch (e: Exception) {
                    showToast(getString(R.string.failed_to_empty_shopping_cart))
                }
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }
}
