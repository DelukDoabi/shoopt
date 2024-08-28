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
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductListAdapter
import com.dedoware.shoopt.persistence.IProductRepository
import com.dedoware.shoopt.persistence.FirebaseProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyseActivity : AppCompatActivity() {
    private lateinit var productListRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var backImageButton: ImageButton

    private var products: List<Product> = emptyList()
    private val productRepository: IProductRepository = FirebaseProductRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyse)
        supportActionBar?.hide()

        progressBar = findViewById(R.id.loading_indicator)
        searchView = findViewById(R.id.search_view)
        productListRecyclerView = findViewById(R.id.product_list_recycler_view)
        backImageButton = findViewById(R.id.back_IB)

        backImageButton.setOnClickListener { finish() }

        progressBar.visibility = View.VISIBLE
        productListRecyclerView.layoutManager = GridLayoutManager(this, 2)
        productListRecyclerView.adapter = ProductListAdapter(emptyList())

        loadProducts()
        addSearch()
    }

    private fun loadProducts() {
        CoroutineScope(Dispatchers.Main).launch {
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
        }
    }

    private fun addSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
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
        productListRecyclerView.adapter = ProductListAdapter(products)
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
    }

    private fun displayAlertOnDeleteProduct(product: Product) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete this product?")
            .setPositiveButton("Delete") { dialog, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val isDeleted = withContext(Dispatchers.IO) {
                        productRepository.deleteProduct(product)
                    }
                    if (isDeleted) {
                        Toast.makeText(
                            this@AnalyseActivity,
                            "Product ${product.name} deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadProducts()
                    } else {
                        Toast.makeText(
                            this@AnalyseActivity,
                            "Failed to delete product",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
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
