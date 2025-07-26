package com.dedoware.shoopt.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.dedoware.shoopt.scanner.BarcodeScannerActivity
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TrackShoppingActivity : AppCompatActivity() {

    private lateinit var addProductButton: MaterialButton
    private lateinit var clearCartListButton: MaterialButton
    private lateinit var backButton: MaterialButton
    private lateinit var emptyStateContainer: LinearLayout

    // Repository instance
    private lateinit var productRepository: IProductRepository

    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }

    private val useFirebase = false // This could be a config or user preference
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_track_shopping)

            // Enregistrement de l'écran de suivi des achats dans Analytics
            try {
                AnalyticsManager.logScreenView("TrackShopping", "TrackShoppingActivity")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
            }

            try {
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
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation du repository: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "repository_init_track")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'application", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            try {
                setupActionButtons()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la configuration des boutons d'action: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "setup_action_buttons")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'interface", Toast.LENGTH_SHORT).show()
            }

            loadShoppingCart()
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans TrackShoppingActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "track_shopping_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Une erreur est survenue lors du démarrage de l'application", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupActionButtons() {
        addProductButton = findViewById(R.id.add_product_IB)
        clearCartListButton = findViewById(R.id.empty_cart_list_IB)
        backButton = findViewById(R.id.back_IB)
        emptyStateContainer = findViewById(R.id.empty_state_container)

        addProductButton.setOnClickListener {
            displayAddProductWayUserChoice()
        }

        clearCartListButton.setOnClickListener {
            showClearCartConfirmationDialog()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadShoppingCart() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val cart = productRepository.getShoppingCart()
                updateCartUI(cart)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du chargement du panier: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "load_shopping_cart")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                showToast(getString(R.string.failed_to_load_shopping_cart))
            }
        }
    }

    private fun displayAddProductWayUserChoice() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.choose_option))
            builder.setMessage(getString(R.string.scan_barcode_or_add_manually))

            builder.setPositiveButton(getString(R.string.scan_barcode)) { _, _ ->
                try {
                    // Analytique pour le choix du scan de code-barres
                    try {
                        AnalyticsManager.logSelectContent("product_input_method", "button", "barcode_scan")
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                    }

                    val intent = Intent(this, BarcodeScannerActivity::class.java)
                    barcodeLauncher.launch(intent)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement du scanner de code-barres: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_scanner_launch")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    showToast("Impossible de lancer le scanner de code-barres")
                }
            }

            builder.setNegativeButton(getString(R.string.add_product_manually)) { _, _ ->
                try {
                    // Analytique pour le choix de l'ajout manuel
                    try {
                        AnalyticsManager.logSelectContent("product_input_method", "button", "manual_input")
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                    }

                    val addProductIntent = Intent(this, AddProductActivity::class.java)
                    addProductIntent.putExtra("source", "TrackShoppingActivity")
                    addProductContract.launch(addProductIntent)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du lancement de l'activité d'ajout de produit: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "add_product_activity_launch")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    showToast("Impossible d'ouvrir l'écran d'ajout de produit")
                }
            }

            val dialog = builder.create()
            dialog.show()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de choix d'ajout: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "display_add_product_choice")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            showToast("Erreur lors de l'affichage des options d'ajout de produit")
        }
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
                            // Analytics pour l'ajout réussi d'un produit au panier (méthode manuelle)
                            try {
                                AnalyticsManager.logUserAction(
                                    "add_product_to_cart",
                                    "shopping_cart",
                                    mapOf(
                                        "method" to "manual_input",
                                        "success" to true
                                    )
                                )
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                            }

                            loadShoppingCart()  // Reload cart after adding the product
                            showToast(getString(R.string.product_added_to_cart))
                        } else {
                            // Analytics pour l'échec d'ajout d'un produit au panier
                            try {
                                AnalyticsManager.logUserAction(
                                    "add_product_to_cart",
                                    "shopping_cart",
                                    mapOf(
                                        "method" to "manual_input",
                                        "success" to false
                                    )
                                )
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                            }

                            showToast(getString(R.string.failed_to_add_product_to_cart))
                        }
                    }
                }
            }
        }

    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcodeValue = result.data?.getStringExtra(BarcodeScannerActivity.BARCODE_RESULT)
            if (barcodeValue != null) {
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        val product = productRepository.getProductByBarcode(barcodeValue.toLong())
                        if (product != null) {
                            // Analytics pour le scan réussi d'un code-barres existant
                            try {
                                AnalyticsManager.logUserAction(
                                    "barcode_scan_success",
                                    "shopping_cart",
                                    mapOf(
                                        "product_found" to true
                                    )
                                )
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                            }

                            val success = productRepository.addProductToCart(product)
                            if (success) {
                                // Analytics pour l'ajout réussi d'un produit au panier via scan
                                try {
                                    AnalyticsManager.logUserAction(
                                        "add_product_to_cart",
                                        "shopping_cart",
                                        mapOf(
                                            "method" to "barcode_scan",
                                            "success" to true
                                        )
                                    )
                                } catch (e: Exception) {
                                    CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                                }

                                loadShoppingCart()  // Reload cart after adding the product
                                showToast(getString(R.string.product_added_to_cart))
                            } else {
                                // Analytics pour l'échec d'ajout d'un produit au panier via scan
                                try {
                                    AnalyticsManager.logUserAction(
                                        "add_product_to_cart",
                                        "shopping_cart",
                                        mapOf(
                                            "method" to "barcode_scan",
                                            "success" to false
                                        )
                                    )
                                } catch (e: Exception) {
                                    CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                                }

                                showToast(getString(R.string.failed_to_add_product_to_cart))
                            }
                        } else {
                            // Analytics pour le scan d'un produit non trouvé
                            try {
                                AnalyticsManager.logUserAction(
                                    "barcode_scan_success",
                                    "shopping_cart",
                                    mapOf(
                                        "product_found" to false
                                    )
                                )
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                            }

                            try {
                                val addProductIntent = Intent(this@TrackShoppingActivity, AddProductActivity::class.java)
                                addProductIntent.putExtra("source", "TrackShoppingActivity")
                                addProductIntent.putExtra("barcode", barcodeValue)
                                addProductContract.launch(addProductIntent)
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors du lancement de l'activité d'ajout avec code-barres: ${e.message ?: "Message non disponible"}")
                                CrashlyticsManager.setCustomKey("error_location", "add_product_with_barcode")
                                CrashlyticsManager.setCustomKey("barcode", barcodeValue)
                                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                                CrashlyticsManager.logException(e)

                                showToast("Impossible d'ouvrir l'écran d'ajout de produit avec le code-barres")
                            }
                        }
                    } catch (e: Exception) {
                        // Analytics pour l'erreur de scan
                        try {
                            AnalyticsManager.logUserAction(
                                "barcode_scan_error",
                                "shopping_cart",
                                mapOf(
                                    "error_message" to (e.message ?: "Message non disponible")
                                )
                            )
                        } catch (ex: Exception) {
                            CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${ex.message ?: "Message non disponible"}")
                        }

                        CrashlyticsManager.log("Erreur lors du traitement du code-barres: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "process_barcode_scan")
                        CrashlyticsManager.setCustomKey("barcode_content", barcodeValue)
                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                        CrashlyticsManager.logException(e)

                        showToast("Erreur lors du traitement du code-barres")
                    }
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                // Analytics pour l'annulation du scan
                try {
                    AnalyticsManager.logUserAction(
                        "barcode_scan_cancelled",
                        "shopping_cart",
                        null
                    )
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                }

                showToast(getString(R.string.cancelled))
            }
        }
    }

    private fun updateCartUI(cart: ShoppingCart?) {
        try {
            val itemCountTextView = findViewById<TextView>(R.id.cart_item_count)
            val totalPriceTextView = findViewById<TextView>(R.id.total_price)

            val itemQuantity = cart?.products?.sumOf { it.quantity } ?: 0
            val totalPrice = cart?.products?.sumOf { it.product.price * it.quantity } ?: 0.0

            itemCountTextView.text = itemQuantity.toString()
            totalPriceTextView.text = String.format("%.2f€", totalPrice)

            val products = cart?.products ?: emptyList()

            // Show/hide empty state and clear button
            if (products.isEmpty()) {
                emptyStateContainer.visibility = View.VISIBLE
                clearCartListButton.visibility = View.GONE
            } else {
                emptyStateContainer.visibility = View.GONE
                clearCartListButton.visibility = View.VISIBLE
            }

            populateProductList(products)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la mise à jour de l'interface du panier: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "update_cart_ui")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            showToast("Erreur lors de l'affichage du panier")
        }
    }

    private fun showToast(messageToDisplay: String) {
        try {
            Toast.makeText(this, messageToDisplay, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // En cas d'erreur lors de l'affichage du toast, on n'enregistre pas dans Crashlytics
            // pour éviter une potentielle boucle infinie si le toast est utilisé pour afficher des erreurs
            e.printStackTrace()
        }
    }

    private fun populateProductList(products: List<CartItem>) {
        try {
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
                    try {
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

                                        // Analytics pour la suppression réussie d'un produit du panier
                                        try {
                                            AnalyticsManager.logUserAction(
                                                "remove_product_from_cart",
                                                "shopping_cart",
                                                mapOf(
                                                    "quantity" to deletedItem.quantity,
                                                    "remaining_products" to updatedProducts.size,
                                                    "success" to true
                                                )
                                            )
                                        } catch (e: Exception) {
                                            CrashlyticsManager.log("Erreur lors de l'enregistrement de l'événement Analytics: ${e.message ?: "Message non disponible"}")
                                        }

                                        // Afficher un message de confirmation
                                        showToast(getString(R.string.product_deleted, deletedItem.product.name))
                                    }
                                } catch (e: Exception) {
                                    CrashlyticsManager.log("Erreur lors de la suppression du produit du panier: ${e.message ?: "Message non disponible"}")
                                    CrashlyticsManager.setCustomKey("error_location", "delete_product_from_cart")
                                    CrashlyticsManager.setCustomKey("product_id", deletedItem.product.id)
                                    CrashlyticsManager.setCustomKey("product_name", deletedItem.product.name)
                                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                                    CrashlyticsManager.logException(e)

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
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors de la gestion du swipe: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "swipe_product_handling")
                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                        CrashlyticsManager.logException(e)

                        showToast("Erreur lors de la suppression du produit")
                        loadShoppingCart() // Recharger le panier en cas d'erreur
                    }
                }
            }

            // Attacher l'ItemTouchHelper au RecyclerView
            ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la population de la liste des produits: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "populate_product_list")
            CrashlyticsManager.setCustomKey("products_count", products.size)
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            showToast("Erreur lors de l'affichage de la liste des produits")
        }
    }

    private fun showClearCartConfirmationDialog() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.clear_shopping_cart))
            builder.setMessage(getString(R.string.clear_shopping_cart_confirm))

            builder.setPositiveButton(getString(R.string.clear)) { _, _ ->
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        val success = productRepository.clearShoppingCart()
                        if (success) {
                            // Analytics pour le vidage réussi du panier
                            try {
                                AnalyticsManager.logUserAction(
                                    "clear_shopping_cart",
                                    "shopping_cart",
                                    mapOf(
                                        "success" to true
                                    )
                                )
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'���vénement Analytics: ${e.message ?: "Message non disponible"}")
                            }

                            loadShoppingCart()  // Reload cart after clearing
                            showToast(getString(R.string.shopping_cart_emptied))
                        } else {
                            showToast(getString(R.string.failed_to_empty_shopping_cart))
                        }
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors de la vidange du panier: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "clear_shopping_cart")
                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                        CrashlyticsManager.logException(e)

                        showToast(getString(R.string.failed_to_empty_shopping_cart))
                    }
                }
            }

            builder.setNegativeButton(android.R.string.cancel, null)
            builder.create().show()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de confirmation de vidange: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "show_clear_cart_dialog")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            showToast("Erreur lors de l'affichage du dialogue de confirmation")
        }
    }
}
