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
import androidx.recyclerview.widget.ItemTouchHelper
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
        val adapter = ProductTrackAdapter(products.toMutableList())
        recyclerView.adapter = adapter

        // Configuration du swipe-to-delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // Nous ne supportons pas le déplacement des éléments
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.getItems()[position]

                // Afficher un dialogue de confirmation avant de supprimer
                val builder = AlertDialog.Builder(this@TrackShoppingActivity)
                builder.setTitle(getString(R.string.delete_product))
                builder.setMessage(getString(R.string.delete_product_confirm))

                builder.setPositiveButton(getString(R.string.delete)) { _, _ ->
                    // Supprimer l'élément de l'adaptateur localement pour une réponse visuelle immédiate
                    adapter.removeAt(position)

                    // Puisqu'il n'y a pas de méthode directe pour supprimer un seul produit du panier,
                    // nous devons reconstruire le panier sans le produit supprimé et le sauvegarder
                    GlobalScope.launch(Dispatchers.Main) {
                        try {
                            // Obtenir le panier actuel
                            val currentCart = productRepository.getShoppingCart()

                            if (currentCart != null) {
                                // Vider complètement le panier actuel
                                productRepository.clearShoppingCart()

                                // Reconstruire le panier sans le produit supprimé
                                // Filtrer tous les produits sauf celui qu'on veut supprimer
                                val updatedProducts = currentCart.products.filter { it.product.id != deletedItem.product.id }

                                // Ajouter tous les produits restants au panier un par un
                                for (item in updatedProducts) {
                                    for (i in 1..item.quantity) {
                                        productRepository.addProductToCart(item.product)
                                    }
                                }

                                // Afficher un message de confirmation
                                showToast(getString(R.string.product_deleted, deletedItem.product.name))
                            }
                        } catch (e: Exception) {
                            showToast(getString(R.string.failed_to_delete_product))
                            loadShoppingCart() // Recharger le panier en cas d'erreur
                        }
                    }
                }

                builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                    // Annuler le swipe et restaurer l'élément
                    adapter.notifyItemChanged(position)
                }

                builder.setOnCancelListener {
                    // Si l'utilisateur ferme le dialogue sans choisir, annuler le swipe
                    adapter.notifyItemChanged(position)
                }

                builder.create().show()
            }
        }

        // Attacher l'ItemTouchHelper au RecyclerView
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
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
