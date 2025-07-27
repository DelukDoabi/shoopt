package com.dedoware.shoopt.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.databinding.ActivityProductsFullscreenBinding
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.model.ProductTrackAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProductsFullscreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductsFullscreenBinding
    private lateinit var productAdapter: ProductTrackAdapter
    private var originalProductList = mutableListOf<CartItem>()
    private var filteredProductList = mutableListOf<CartItem>()
    private var isSelectionMode = false

    companion object {
        const val EXTRA_PRODUCTS = "extra_products"
        const val RESULT_PRODUCTS_UPDATED = "result_products_updated"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductsFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        setupRecyclerView()
        loadProductsFromIntent()
        setupEventListeners()
        setupSearchFunctionality()

        // Animate entrance
        animateEntrance()
    }

    private fun setupSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductTrackAdapter(filteredProductList)

        binding.fullscreenProductRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProductsFullscreenActivity)
            adapter = productAdapter
            setHasFixedSize(true)
        }

        // Swipe to delete functionality
        val itemTouchHelper = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val product = filteredProductList[position]
                    handleDeleteProduct(product)
                }
            }
        }

        ItemTouchHelper(itemTouchHelper).attachToRecyclerView(binding.fullscreenProductRecyclerView)
    }

    private fun loadProductsFromIntent() {
        try {
            // Use JSON deserialization instead of Parcelable to avoid corruption
            val productsJson = intent.getStringExtra(EXTRA_PRODUCTS)

            if (!productsJson.isNullOrEmpty()) {
                val gson = Gson()
                val type = object : TypeToken<List<CartItem>>() {}.type
                val products: List<CartItem> = gson.fromJson(productsJson, type)

                originalProductList.clear()
                originalProductList.addAll(products)
                filteredProductList.clear()
                filteredProductList.addAll(products)
                updateUI()
            }
        } catch (e: Exception) {
            // Handle JSON parsing errors gracefully
            originalProductList.clear()
            filteredProductList.clear()
            updateUI()
        }
    }

    private fun setupEventListeners() {
        // Close fullscreen button
        binding.closeFullscreenButton.setOnClickListener {
            finishWithAnimation()
        }

        // Minimize fullscreen button
        binding.minimizeFullscreenButton.setOnClickListener {
            finishWithAnimation()
        }

        // Filter button
        binding.filterProductsButton.setOnClickListener {
            // TODO: Implement filter functionality
            showFilterOptions()
        }

        // Quick add products button (in empty state)
        binding.quickAddProductsButton.setOnClickListener {
            finishWithAnimation()
        }

        // Selection mode buttons
        binding.selectAllButton.setOnClickListener {
            selectAllProducts()
        }

        binding.clearAllButton.setOnClickListener {
            clearSelectedProducts()
        }
    }

    private fun setupSearchFunctionality() {
        binding.searchProductsEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterProducts(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterProducts(query: String) {
        filteredProductList.clear()

        if (query.isEmpty()) {
            filteredProductList.addAll(originalProductList)
        } else {
            val searchQuery = query.lowercase().trim()
            filteredProductList.addAll(
                originalProductList.filter { product ->
                    product.product.name.lowercase().contains(searchQuery) ||
                    product.product.shop.lowercase().contains(searchQuery)
                }
            )
        }

        productAdapter.notifyDataSetChanged()
        updateUI()
    }

    private fun updateUI() {
        val productCount = filteredProductList.size

        // Update product count badge
        binding.fullscreenProductCountBadge.text = productCount.toString()

        // Show/hide empty state
        if (productCount == 0) {
            binding.fullscreenProductRecyclerView.visibility = View.GONE
            binding.fullscreenEmptyStateContainer.visibility = View.VISIBLE
        } else {
            binding.fullscreenProductRecyclerView.visibility = View.VISIBLE
            binding.fullscreenEmptyStateContainer.visibility = View.GONE
        }

        // Update selection mode UI
        binding.fullscreenBottomBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    }

    private fun handleProductClick(product: CartItem) {
        if (isSelectionMode) {
            toggleProductSelection(product)
        } else {
            // TODO: Open product details or edit
            showProductDetails(product)
        }
    }

    private fun handleProductLongClick(product: CartItem) {
        if (!isSelectionMode) {
            enterSelectionMode()
        }
        toggleProductSelection(product)
    }

    private fun handleDeleteProduct(product: CartItem) {
        originalProductList.remove(product)
        filteredProductList.remove(product)
        productAdapter.notifyDataSetChanged()
        updateUI()

        Snackbar.make(binding.root, "Product deleted", Snackbar.LENGTH_SHORT)
            .setAction("Undo") {
                originalProductList.add(product)
                filterProducts(binding.searchProductsEditText.text.toString())
            }
            .show()
    }

    private fun toggleProductSelection(product: CartItem) {
        // TODO: Implement selection logic with adapter
        productAdapter.notifyDataSetChanged()
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        updateUI()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        updateUI()
    }

    private fun selectAllProducts() {
        // TODO: Implement select all logic
        Snackbar.make(binding.root, "All products selected", Snackbar.LENGTH_SHORT).show()
    }

    private fun clearSelectedProducts() {
        // TODO: Implement clear selected logic
        exitSelectionMode()
        Snackbar.make(binding.root, "Selection cleared", Snackbar.LENGTH_SHORT).show()
    }

    private fun showFilterOptions() {
        // TODO: Implement filter dialog
        Snackbar.make(binding.root, "Filter options coming soon", Snackbar.LENGTH_SHORT).show()
    }

    private fun showProductDetails(product: CartItem) {
        // TODO: Implement product details view
        Snackbar.make(binding.root, "Product: ${product.product.name}", Snackbar.LENGTH_SHORT).show()
    }

    private fun animateEntrance() {
        // Fade in animation for the entire layout
        binding.root.alpha = 0f
        binding.root.scaleX = 0.9f
        binding.root.scaleY = 0.9f

        val fadeIn = ObjectAnimator.ofFloat(binding.root, "alpha", 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(binding.root, "scaleX", 0.9f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.root, "scaleY", 0.9f, 1f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleX, scaleY)
        animatorSet.duration = 300
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }

    private fun finishWithAnimation() {
        // Fade out animation
        val fadeOut = ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(binding.root, "scaleX", 1f, 0.9f)
        val scaleY = ObjectAnimator.ofFloat(binding.root, "scaleY", 1f, 0.9f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeOut, scaleX, scaleY)
        animatorSet.duration = 200
        animatorSet.interpolator = DecelerateInterpolator()

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                finish()
                overridePendingTransition(0, 0)
            }
        })

        animatorSet.start()
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            finishWithAnimation()
        }
    }

    override fun finish() {
        // Return updated products list using JSON serialization to avoid corruption
        val intent = Intent()
        val gson = Gson()
        val updatedProductsJson = gson.toJson(originalProductList)
        intent.putExtra(RESULT_PRODUCTS_UPDATED, updatedProductsJson)
        setResult(RESULT_OK, intent)
        super.finish()
    }
}
