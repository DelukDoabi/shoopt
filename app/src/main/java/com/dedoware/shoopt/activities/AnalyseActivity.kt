package com.dedoware.shoopt.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductListAdapter
import com.dedoware.shoopt.utils.ShooptUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class AnalyseActivity : AppCompatActivity() {
    private lateinit var productListRecyclerView: RecyclerView

    private lateinit var progressBar: ProgressBar
    private lateinit var searchView: SearchView

    private var products: List<Product> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_analyse)

        supportActionBar?.hide()

        progressBar = findViewById(R.id.loading_indicator)
        searchView = findViewById(R.id.search_view)
        productListRecyclerView = findViewById(R.id.product_list_recycler_view)

        progressBar.visibility = View.VISIBLE
        productListRecyclerView.layoutManager = GridLayoutManager(this, 2)
        productListRecyclerView.adapter = ProductListAdapter(emptyList())

        ShooptUtils.doAfterInitFirebase(baseContext) { getProductsFromRTDB() }

        addSearch()
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

        searchView.setOnClickListener {
            searchView.isIconified = false
        }

        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                searchView.isIconified = false
            }
        }

        searchView.setOnCloseListener {
            setupAdapter(products)
            false
        }
    }

    private fun getProductsFromRTDB() {
        val productsReference =
            ShooptUtils.getFirebaseDatabaseReference().child("products")

        productsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                products = mutableListOf()
                dataSnapshot.children.forEach { productData ->
                    val product = productData.getValue(Product::class.java)
                    product?.let { (products as MutableList<Product>).add(it) }
                }
                if (products.isNotEmpty()) {
                    setupAdapter(products.sortedByDescending { it.timestamp })
                    progressBar.visibility = View.GONE
                } else {
                    Log.d("SHOOPT_TAG", "No product found!")
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.d("SHOOPT_TAG", databaseError.details)
                Toast.makeText(this@AnalyseActivity, databaseError.details, Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun setupAdapter(products: List<Product>) {
        productListRecyclerView.adapter = ProductListAdapter(products)
        productListRecyclerView.adapter?.let { adapter ->
            if (adapter is ProductListAdapter) {
                adapter.setOnLongClickListener { product ->
                    Toast.makeText(
                        this@AnalyseActivity,
                        "Long pressed on product: ${product.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    supportActionBar?.show()
                }
            } else {
                Log.d("SHOOPT_TAG", "Adapter is NOT an instance of ProductListAdapter")
            }
        }
    }
}