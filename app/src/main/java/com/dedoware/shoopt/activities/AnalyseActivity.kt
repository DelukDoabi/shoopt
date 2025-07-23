package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductListAdapter
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import com.dedoware.shoopt.persistence.LocalProductRepository
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyseActivity : AppCompatActivity() {
    private lateinit var productListRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var backImageButton: ImageButton
    private lateinit var userPreferences: UserPreferences

    private var products: List<Product> = emptyList()
    private lateinit var productRepository: IProductRepository

    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }

    private val useFirebase = false // This could be a config or user preference


    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_analyse)

            // Enregistrement de l'écran dans Analytics
            try {
                AnalyticsManager.logScreenView("Analyze", "AnalyseActivity")

            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "analytics_screen_log")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            // Initialiser les préférences utilisateur
            try {
                userPreferences = UserPreferences(this)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation des préférences utilisateur: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "user_preferences_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
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
                CrashlyticsManager.setCustomKey("error_location", "repository_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de la base de données", Toast.LENGTH_SHORT).show()
            }

            try {
                progressBar = findViewById(R.id.loading_indicator)
                searchView = findViewById(R.id.search_view)
                productListRecyclerView = findViewById(R.id.product_list_recycler_view)
                backImageButton = findViewById(R.id.back_IB)

                backImageButton.setOnClickListener { finish() }

                progressBar.visibility = View.VISIBLE
                productListRecyclerView.layoutManager = GridLayoutManager(this, 2)
                productListRecyclerView.adapter = ProductListAdapter(emptyList(), userPreferences)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de l'interface: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "ui_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'interface", Toast.LENGTH_SHORT).show()
            }

            try {
                loadProducts()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du chargement des produits: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "load_products")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                progressBar.visibility = View.GONE
                Toast.makeText(this, "Impossible de charger les produits", Toast.LENGTH_SHORT).show()
            }

            try {
                addSearch()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de la recherche: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "search_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans onCreate d'AnalyseActivity: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "analyse_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Une erreur est survenue lors du démarrage de l'application", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadProducts() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                products = withContext(Dispatchers.IO) {
                    productRepository.getAll()
                }
                if (products.isNotEmpty()) {
                    setupAdapter(products.sortedByDescending { it.timestamp })
                    progressBar.visibility = View.GONE
                } else {
                    Log.d("SHOOPT_TAG", "No products found!")
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du chargement des produits: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "load_products_coroutine")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@AnalyseActivity, "Erreur lors du chargement des produits", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Analytique pour la recherche soumise (sans collecter le terme exact)
                if (!query.isNullOrEmpty()) {
                    val params = Bundle().apply {
                        putBoolean("search_performed", true)
                    }
                    AnalyticsManager.logCustomEvent("product_search", params)
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredProducts = products.filter {
                    it.name.contains(newText!!, ignoreCase = true)
                }
                setupAdapter(filteredProducts)
                return true
            }
        })

        searchView.setOnClickListener { searchView.isIconified = false }
        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) searchView.isIconified = false
        }
        searchView.setOnCloseListener {
            setupAdapter(products)
            false
        }
    }

    private fun setupAdapter(products: List<Product>) {
        productListRecyclerView.adapter = ProductListAdapter(products, userPreferences)
        (productListRecyclerView.adapter as? ProductListAdapter)?.apply {
            setOnLongClickListener { product ->
                showContextMenu(product)
                true
            }
            setOnItemClickListener { product ->
                openAddProductActivity(product)
            }
        }
    }

    private val addProductLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadProducts() // Reload products to reflect changes
        }
    }

    private fun openAddProductActivity(product: Product) {
        try {
            val intent = Intent(this, AddProductActivity::class.java).apply {
                putExtra("productId", product.id)
                putExtra("barcode", product.barcode)
                putExtra("name", product.name)
                putExtra("shop", product.shop)
                putExtra("price", product.price)
                putExtra("unitPrice", product.unitPrice)
                putExtra("pictureUrl", product.pictureUrl)
            }
            addProductLauncher.launch(intent)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'ouverture de l'activité d'édition de produit: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "open_add_product")
            CrashlyticsManager.setCustomKey("product_id", product.id)
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Impossible d'ouvrir l'éditeur de produit", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayAlertOnDeleteProduct(product: Product) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_product))
            .setMessage(getString(R.string.delete_product_confirm))
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val isDeleted = withContext(Dispatchers.IO) {
                            productRepository.deleteProduct(product)
                        }
                        if (isDeleted) {
                            Toast.makeText(
                                this@AnalyseActivity,
                                getString(R.string.product_deleted, product.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            loadProducts()
                        } else {
                            Toast.makeText(
                                this@AnalyseActivity,
                                getString(R.string.failed_to_delete_product),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        dialog.dismiss()
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors de la suppression du produit: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "delete_product")
                        CrashlyticsManager.setCustomKey("product_id", product.id)
                        CrashlyticsManager.setCustomKey("product_name", product.name)
                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                        CrashlyticsManager.logException(e)

                        Toast.makeText(
                            this@AnalyseActivity,
                            getString(R.string.failed_to_delete_product),
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancelled)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        alertDialog.show()
    }

    private fun showContextMenu(product: Product) {
        val menuItems = arrayOf("Delete", "Modify")

        val builder = AlertDialog.Builder(this)
        builder.setItems(menuItems) { _, which ->
            when (which) {
                0 -> displayAlertOnDeleteProduct(product)
                1 -> openAddProductActivity(product)
            }
        }
        builder.create().show()
    }

    override fun onResume() {
        super.onResume()
        loadProducts() // Reload products to ensure UI is updated with the latest data
    }
}
