package com.dedoware.shoopt.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductListAdapter
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import com.dedoware.shoopt.persistence.LocalProductRepository
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.dedoware.shoopt.analytics.AnalyticsService
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences
import com.dedoware.shoopt.utils.getCurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
// Imports pour le système de spotlight
import com.dedoware.shoopt.extensions.startSpotlightTour
import com.dedoware.shoopt.extensions.createSpotlightItem
import com.dedoware.shoopt.extensions.isSpotlightAvailable
import com.dedoware.shoopt.models.SpotlightShape

class AnalyseActivity : AppCompatActivity() {
    private lateinit var productListRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var backButton: MaterialButton
    private lateinit var userPreferences: UserPreferences
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var addButton: MaterialButton

    // New UI elements for modern layout
    private lateinit var productCountBadge: TextView
    private lateinit var totalProductsCount: TextView
    private lateinit var averagePrice: TextView
    private lateinit var uniqueShopsCount: TextView
    private lateinit var sortFilterButton: MaterialButton


    // UI elements for currency conversion
    private lateinit var statsContainer: View
    private lateinit var emptyStateContainer: View
    private lateinit var productCountTV: TextView
    private lateinit var avgPriceTV: TextView
    private lateinit var shopsCountTV: TextView

    private lateinit var productRepository: IProductRepository

    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }

    private val useFirebase = false // This could be a config or user preference

    // Register the launcher for our ML Kit-based scanner
    private val barcodeScannerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result: androidx.activity.result.ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val barcodeValue = result.data?.getStringExtra(com.dedoware.shoopt.scanner.BarcodeScannerActivity.BARCODE_RESULT)
            if (barcodeValue != null) {
                try {
                    // Tracker le succès du scan
                    try {
                        AnalyticsService.getInstance(ShooptApplication.instance).trackScanSuccess("barcode")
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors du tracking scan success: ${e.message}")
                    }

                    // Vérifier si le produit existe déjà avant de lancer l'activité
                    checkProductExistenceAndNavigate(barcodeValue)

                    // Analytics pour le scan de code-barres
                    val params = Bundle().apply {
                        putString("barcode_length", barcodeValue.length.toString())
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("barcode_scanned", params)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du traitement du code-barres: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "barcode_processing")
                    CrashlyticsManager.setCustomKey("barcode", barcodeValue)
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, getString(R.string.barcode_processing_error), Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    AnalyticsService.getInstance(ShooptApplication.instance).trackScanFailed("barcode", "no_value_returned")
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors du tracking de l'échec du scan (pas de valeur): ${e.message}")
                }
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            try {
                AnalyticsService.getInstance(ShooptApplication.instance).trackScanFailed("barcode", "user_cancelled")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du tracking de l'annulation du scan: ${e.message}")
            }
            Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_analyse)

            // Enregistrement de l'écran dans Analytics
            try {
                AnalyticsService.getInstance(ShooptApplication.instance).trackScreenView("Analyze", "AnalyseActivity")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'enregistrement de l'écran dans Analytics: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "analytics_screen_log")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            // Initialiser les préférences utilisateur
            try {
                userPreferences = UserPreferences.getInstance(this)
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
                initializeViews()
                setupClickListeners()
                loadProducts()
                addSearch()

                // Démarrer le système de spotlight si nécessaire
                setupSpotlightTour()
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation de l'interface: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "ui_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'interface", Toast.LENGTH_SHORT).show()
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

    private var products: List<Product> = emptyList()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private fun initializeViews() {
        progressBar = findViewById(R.id.loading_indicator)
        searchView = findViewById(R.id.search_view)
        productListRecyclerView = findViewById(R.id.product_list_recycler_view)
        backButton = findViewById(R.id.back_IB)
        emptyStateLayout = findViewById(R.id.empty_state_layout)
        addButton = findViewById(R.id.add_product_button)

        // New modern UI elements
        productCountBadge = findViewById(R.id.product_count_badge)
        totalProductsCount = findViewById(R.id.total_products_count)
        averagePrice = findViewById(R.id.average_price)
        uniqueShopsCount = findViewById(R.id.unique_shops_count)
        sortFilterButton = findViewById(R.id.sort_filter_button)

        // UI elements for currency conversion
        statsContainer = findViewById(R.id.stats_card)
        emptyStateContainer = findViewById(R.id.empty_state_layout)

        // Utiliser les vrais identifiants disponibles
        productCountTV = totalProductsCount // Utiliser totalProductsCount pour productCountTV
        avgPriceTV = averagePrice // Utiliser averagePrice pour avgPriceTV
        shopsCountTV = uniqueShopsCount // Utiliser uniqueShopsCount pour shopsCountTV

        productListRecyclerView.layoutManager = GridLayoutManager(this, 2)
        productListRecyclerView.adapter = ProductListAdapter(emptyList(), userPreferences)

        // Ajouter l'observation des changements de devise
        observeCurrencyChanges()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        addButton.setOnClickListener {
            // Show the same add product options as in MainActivity
            showAddProductOptions()
        }

        sortFilterButton.setOnClickListener {
            showSortFilterDialog()
        }
    }

    private fun loadProducts() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                progressBar.visibility = View.VISIBLE
                productListRecyclerView.visibility = View.GONE
                emptyStateLayout.visibility = View.GONE

                products = withContext(Dispatchers.IO) {
                    productRepository.getAll()
                }

                if (products.isNotEmpty()) {
                    setupAdapter(products.sortedByDescending { it.timestamp })
                    updateStatsDisplay(products)
                    progressBar.visibility = View.GONE
                    productListRecyclerView.visibility = View.VISIBLE
                    emptyStateLayout.visibility = View.GONE
                } else {
                    Log.d("SHOOPT_TAG", "No products found!")
                    progressBar.visibility = View.GONE
                    productListRecyclerView.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                    updateStatsDisplay(emptyList())
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

    private fun updateStatsDisplay(productList: List<Product>) {
        try {
            val count = productList.size
            productCountBadge.text = count.toString()
            totalProductsCount.text = count.toString()

            // Gérer la visibilité de stats_card selon l'état de la liste
            if (productList.isEmpty()) {
                statsContainer.visibility = View.GONE
                emptyStateContainer.visibility = View.VISIBLE
                // Réinitialiser les valeurs à zéro
                averagePrice.text = "0.00€"
                uniqueShopsCount.text = "0"
                return
            }

            // Si la liste n'est pas vide, afficher stats_card
            statsContainer.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE

            // Calculate average price
            val validPrices = productList.mapNotNull { it.price }.filter { it > 0.0 }
            val avgPrice = if (validPrices.isNotEmpty()) validPrices.average() else 0.0
            val formatter = DecimalFormat("#.##")
            averagePrice.text = "${formatter.format(avgPrice)}€"

            // Calculate unique shops count
            val uniqueShops = productList.mapNotNull { it.shop }
                .filter { it.isNotBlank() && it != "Unknown Shop" }
                .distinct()
                .size
            uniqueShopsCount.text = uniqueShops.toString()

            // Log analytics for stats
            val analyticsBundle = Bundle().apply {
                putInt("total_products", count)
                putDouble("average_price", avgPrice)
                putInt("unique_shops", uniqueShops)
            }
            AnalyticsService.getInstance(ShooptApplication.instance).logEvent("analyze_stats_viewed", analyticsBundle)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la mise à jour des statistiques: ${e.message}")
            CrashlyticsManager.logException(e)
        }
    }

    private fun showSortFilterDialog() {
        val sortOptions = arrayOf(
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_price_asc),
            getString(R.string.sort_price_desc),
            getString(R.string.sort_shop_asc),
            getString(R.string.sort_recent_first),
            getString(R.string.sort_oldest_first)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort_products))
            .setItems(sortOptions) { _, which ->
                sortProducts(which)
            }
            .create()
            .show()
    }

    private fun sortProducts(sortType: Int) {
        val sortedProducts = when (sortType) {
            0 -> products.sortedBy { it.name.lowercase() }
            1 -> products.sortedByDescending { it.name.lowercase() }
            2 -> products.sortedBy { it.price ?: Double.MAX_VALUE }
            3 -> products.sortedByDescending { it.price ?: 0.0 }
            4 -> products.sortedBy { it.shop?.lowercase() ?: "zzz" }
            5 -> products.sortedByDescending { it.timestamp }
            6 -> products.sortedBy { it.timestamp }
            else -> products
        }
        setupAdapter(sortedProducts)
        // Mettre à jour les statistiques avec la liste triée
        updateStatsDisplay(sortedProducts)
    }

    private fun addSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Analytique pour la recherche soumise (sans collecter le terme exact)
                if (!query.isNullOrEmpty()) {
                    val params = Bundle().apply {
                        putBoolean("search_performed", true)
                        putInt("search_length", query.length)
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("product_search", params)
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredProducts = if (newText.isNullOrEmpty()) {
                    products
                } else {
                    products.filter { product ->
                        product.name.contains(newText, ignoreCase = true) ||
                        product.shop?.contains(newText, ignoreCase = true) == true ||
                        product.price?.toString()?.contains(newText) == true ||
                        product.unitPrice?.toString()?.contains(newText) == true
                    }
                }
                setupAdapter(filteredProducts)
                // Mettre à jour les statistiques avec la liste filtrée
                updateStatsDisplay(filteredProducts)
                return true
            }
        })

        searchView.setOnClickListener { searchView.isIconified = false }
        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) searchView.isIconified = false
        }
        searchView.setOnCloseListener {
            setupAdapter(products)
            // Restaurer les statistiques avec la liste complète
            updateStatsDisplay(products)
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
                            // Tracker la suppression de produit
                            try {
                                ShooptApplication.instance.analyticsService.trackProductDelete(product.id, product.name)
                            } catch (e: Exception) {
                                CrashlyticsManager.log("Erreur lors du tracking de la suppression de produit: ${e.message}")
                            }

                            // Message de confirmation - utiliser la ressource localisée
                            val deletedMsg = getString(R.string.product_deleted).replace("%1\$s", product.name)
                            Toast.makeText(
                                this@AnalyseActivity,
                                deletedMsg,
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

    private fun showAddProductOptions() {
        try {
            // Analytics pour le passage à l'écran d'ajout de produit
            // Remplacer l'ancien AnalyticsManager par AnalyticsService
            val selectContentParams = Bundle().apply {
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.CONTENT_TYPE, "navigation")
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_ID, "button")
                putString(com.google.firebase.analytics.FirebaseAnalytics.Param.ITEM_NAME, "add_update_product")
            }
            AnalyticsService.getInstance(ShooptApplication.instance)
                .logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SELECT_CONTENT, selectContentParams)

            // Lancer la nouvelle activité de choix de produit avec animation en utilisant le nom complet de la classe
            val intent = Intent(this, com.dedoware.shoopt.activities.ProductChoiceActivity::class.java)
            startActivity(intent)
            
            // Utiliser nos animations personnalisées maintenant qu'elles sont implémentées
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } catch (e: Exception) {
            // Capture des erreurs
            CrashlyticsManager.log("Erreur lors du lancement de ProductChoiceActivity: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
            Toast.makeText(this, getString(R.string.add_product_options_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkProductExistenceAndNavigate(barcode: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val barcodeAsLong = barcode.toLongOrNull() ?: 0L

                // Vérifier si le produit existe déjà dans la base de données
                val existingProduct = withContext(Dispatchers.IO) {
                    productRepository.getProductByBarcode(barcodeAsLong)
                }

                if (existingProduct != null) {
                    // Si le produit existe, ouvrir avec toutes ses informations
                    openAddProductActivity(existingProduct)

                    // Analytics pour le chargement d'un produit existant
                    val existingProductParams = Bundle().apply {
                        putString("source", "barcode_scan")
                        putString("product_found", "true")
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("existing_product_loaded", existingProductParams)
                } else {
                    // Si le produit n'existe pas, créer un nouveau produit avec le code-barres
                    // Using the proper constructor instead of trying to assign to val properties
                    val newProduct = Product(
                        id = "",
                        barcode = barcodeAsLong,
                        timestamp = 0,
                        name = "",
                        price = 0.0,
                        unitPrice = 0.0,
                        shop = "",
                        pictureUrl = ""
                    )
                    openAddProductActivity(newProduct)

                    // Analytics pour la création d'un nouveau produit
                    val newProductParams = Bundle().apply {
                        putString("source", "barcode_scan")
                        putString("product_found", "false")
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("new_product_created", newProductParams)
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la vérification du produit: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "product_existence_check")
                CrashlyticsManager.setCustomKey("barcode", barcode)
                CrashlyticsManager.logException(e)

                Toast.makeText(this@AnalyseActivity, getString(R.string.product_check_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Configure et démarre le tour de spotlight pour guider l'utilisateur
     * sur les fonctionnalités principales de l'écran d'analyse
     */
    private fun setupSpotlightTour() {
        try {
            // Vérifier si le spotlight doit être affiché
            if (!isSpotlightAvailable()) {
                return
            }

            // Créer la liste des éléments à mettre en surbrillance
            val spotlightItems = mutableListOf<com.dedoware.shoopt.models.SpotlightItem>()

            // Spotlight pour les statistiques - Badge de comptage des produits
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productCountBadge,
                    titleRes = R.string.spotlight_analyse_stats_title,
                    descriptionRes = R.string.spotlight_analyse_stats_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Spotlight pour la barre de recherche
            spotlightItems.add(
                createSpotlightItem(
                    targetView = searchView,
                    titleRes = R.string.spotlight_analyse_search_title,
                    descriptionRes = R.string.spotlight_analyse_search_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le bouton de tri/filtre
            spotlightItems.add(
                createSpotlightItem(
                    targetView = sortFilterButton,
                    titleRes = R.string.spotlight_analyse_sort_title,
                    descriptionRes = R.string.spotlight_analyse_sort_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le bouton d'ajout de produit
            spotlightItems.add(
                createSpotlightItem(
                    targetView = addButton,
                    titleRes = R.string.spotlight_analyse_add_title,
                    descriptionRes = R.string.spotlight_analyse_add_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Spotlight pour la liste de produits (si elle contient des produits)
            if (products.isNotEmpty()) {
                spotlightItems.add(
                    createSpotlightItem(
                        targetView = productListRecyclerView,
                        titleRes = R.string.spotlight_analyse_list_title,
                        descriptionRes = R.string.spotlight_analyse_list_description,
                        shape = SpotlightShape.ROUNDED_RECTANGLE
                    )
                )
            }

            // Démarrer le tour de spotlight avec un léger délai pour que l'interface soit prête
            window.decorView.post {
                startSpotlightTour(spotlightItems) {
                    // Callback appelé à la fin du tour
                    val spParams = Bundle().apply {
                        putString("action", "spotlight_tour_completed")
                        putString("category", "onboarding")
                        putString("screen", "AnalyseActivity")
                    }
                    AnalyticsService.getInstance(ShooptApplication.instance).logEvent("user_action", spParams)
                }
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du spotlight: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "setup_spotlight_tour")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onResume() {
        super.onResume()
        loadProducts() // Reload products to ensure UI is updated with the latest data
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: mettre à jour l'intent de l'activité

        // Vérifier si on doit forcer un refresh des spotlights
        val forceRefresh = intent?.getBooleanExtra("force_spotlight_refresh", false) ?: false
        if (forceRefresh) {
            CrashlyticsManager.log("AnalyseActivity onNewIntent: Force spotlight refresh requested")

            // Vérifier si l'onboarding est complété et forcer les spotlights
            if (UserPreferences.isOnboardingCompleted(this)) {
                // Délai court pour laisser l'interface se stabiliser
                window.decorView.postDelayed({
                    setupSpotlightTour()
                }, 500)
            }
        }
    }

    // Méthode pour observer les changements de devise
    private fun observeCurrencyChanges() {
        getCurrencyManager().currencyChangeEvent.observe(this, Observer { newCurrencyCode ->
            // Rafraîchir l'interface utilisateur lorsque la devise change
            Toast.makeText(this, getString(R.string.currency_changed, newCurrencyCode), Toast.LENGTH_SHORT).show()

            // Recharger l'adaptateur avec les produits existants pour déclencher la conversion
            setupAdapter(products)

            // Mettre à jour les statistiques avec les nouveaux prix convertis
            updateStats(products)
        })
    }

    // Mise à jour de la méthode updateStats pour utiliser la conversion de devises
    private fun updateStats(products: List<Product>) {
        if (products.isEmpty()) {
            statsContainer.visibility = View.GONE
            emptyStateContainer.visibility = View.VISIBLE
            return
        }

        statsContainer.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE

        // Nombre de produits
        val productCount = products.size
        productCountTV.text = productCount.toString()

        // Calcul du prix moyen avec conversion
        coroutineScope.launch {
            try {
                var totalPrice = 0.0
                val currencyManager = getCurrencyManager()

                // Convertir tous les prix avant de calculer la moyenne
                for (product in products) {
                    val convertedPrice = currencyManager.convertToCurrentCurrencySuspend(product.price, "EUR")
                    totalPrice += convertedPrice
                }

                val avgPrice = if (productCount > 0) totalPrice / productCount else 0.0
                val formattedAvgPrice = currencyManager.formatPrice(avgPrice)

                withContext(Dispatchers.Main) {
                    avgPriceTV.text = formattedAvgPrice
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du calcul des statistiques: ${e.message}")
                withContext(Dispatchers.Main) {
                    // En cas d'erreur, utiliser le format simple
                    val avgPrice = products.map { it.price }.average()
                    avgPriceTV.text = userPreferences.formatPrice(avgPrice)
                }
            }
        }

        // Nombre de magasins
        val shopCount = products.map { it.shop }.distinct().size
        shopsCountTV.text = shopCount.toString()
    }
}
