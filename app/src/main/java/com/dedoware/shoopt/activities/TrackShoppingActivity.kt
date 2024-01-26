package com.dedoware.shoopt.activities

import CartItem
import ProductTrackAdapter
import ShoppingCart
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
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.utils.ShooptUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TrackShoppingActivity : ComponentActivity() {
    private lateinit var addProductImageButton: ImageButton
    private lateinit var clearCartImageButton: ImageButton
    private lateinit var backImageButton: ImageButton
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_track_shopping)

        setupActionButtons()

        ShooptUtils.doAfterInitFirebase(baseContext) {
            loadShoppingCart()
        }
    }

    private fun setupActionButtons() {
        addProductImageButton =
            findViewById(R.id.add_product_IB)

        addProductImageButton.setOnClickListener {
            displayAddProductWayUserChoice()
        }

        clearCartImageButton =
            findViewById(R.id.empty_cart_IB)

        clearCartImageButton.setOnClickListener {
            showClearCartConfirmationDialog()
        }

        backImageButton = findViewById(R.id.back_IB)

        backImageButton.setOnClickListener {
            finish()
        }
    }

    private fun loadShoppingCart() {
        val cartReference = FirebaseDatabase.getInstance().reference.child("shoppingCart")

        cartReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val currentCart = dataSnapshot.getValue(ShoppingCart::class.java)

                // Update the UI with the latest data
                updateCartUI(currentCart)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                showToast("Failed to load shopping cart")
            }
        })
    }

    private fun displayAddProductWayUserChoice() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose an option")
        builder.setMessage("Scan barcode or add product manually")

        builder.setPositiveButton("Scan barcode") { _, _ ->
            // Launch the barcode scanner
            barcodeLauncher.launch(ScanOptions())
        }

        builder.setNegativeButton("Add product manually") { _, _ ->
            // Launch the add product manually activity
            val addProductIntent = Intent(this, AddProductActivity::class.java)
            addProductIntent.putExtra("source", "TrackShoppingActivity")
            addProductContract.launch(Intent(addProductIntent))
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
                    addProductToShoppingCart(product)
                }
            }
        }


    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            showToast("Cancelled")
        } else {
            getProductByBarcodeFromDB(result.contents.toLong()) { product ->
                if (product != null) {
                    addProductToShoppingCart(product)
                } else {
                    val addProductIntent =
                        Intent(this, AddProductActivity::class.java)
                    addProductIntent.putExtra("source", "TrackShoppingActivity")
                    addProductIntent.putExtra("barcode", result.contents)
                    addProductContract.launch(Intent(addProductIntent))
                }
            }
        }
    }

    private fun addProductToShoppingCart(product: Product) {
        // First, retrieve the current shopping cart from Firebase
        val cartReference = FirebaseDatabase.getInstance().reference.child("shoppingCart")

        cartReference.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val currentCart = dataSnapshot.getValue(ShoppingCart::class.java)

                if (currentCart != null) {
                    // If the cart exists, add the product to the list of cart items
                    val cartItems = currentCart.products

                    // Check if the product already exists in the cart
                    val existingCartItem = cartItems.find { it.product.barcode == product.barcode }

                    if (existingCartItem != null) {
                        // If the product exists, update the quantity
                        existingCartItem.quantity += 1
                    } else {
                        // If the product is not in the cart, add it as a new cart item
                        cartItems.add(CartItem(product, 1))
                    }

                    // Update the cart in Firebase
                    cartReference.setValue(currentCart)

                    updateCartUI(currentCart)

                    // Optionally, you can display a message to the user indicating success
                    showToast("Product added to the shopping cart")
                } else {
                    // If the cart does not exist, create a new cart with the added product
                    val newCart = ShoppingCart(mutableListOf(CartItem(product, 1)))

                    // Set the new cart in Firebase
                    cartReference.setValue(newCart)

                    updateCartUI(newCart)

                    // Optionally, you can display a message to the user indicating success
                    showToast("Product added to the shopping cart")
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle database errors
                showToast("Failed to add product to the shopping cart")
            }
        })
    }


    private fun getProductByBarcodeFromDB(barcode: Long, callback: (Product?) -> Unit) {
        val productsReference = ShooptUtils.getFirebaseDatabaseReference().child("products")

        val query: Query = productsReference.orderByChild("barcode").equalTo(barcode.toDouble())

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Barcode exists, retrieve the product
                    for (productSnapshot in dataSnapshot.children) {
                        val product = productSnapshot.getValue(Product::class.java)
                        callback(product)
                        return
                    }
                } else {
                    // Barcode doesn't exist, return null
                    callback(null)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle database errors
                callback(null)
            }
        })
    }

    private fun updateCartUI(cart: ShoppingCart?) {
        val itemCountTextView = findViewById<TextView>(R.id.cart_item_count)
        val totalPriceTextView = findViewById<TextView>(R.id.total_price)

        val itemQuantity = cart?.products?.sumOf { it.quantity } ?: 0
        val totalPrice = cart?.products?.sumOf { it.product.price * it.quantity } ?: 0.0

        itemCountTextView.text = itemQuantity.toString()
        totalPriceTextView.text = String.format("%.2f", totalPrice)

        // Populate the product list
        if (cart != null) {
            populateProductList(cart.products)
        } else {
            populateProductList(mutableListOf())
        }
    }

    private fun showToast(messageToDisplay: String) {
        Toast.makeText(this@TrackShoppingActivity, messageToDisplay, Toast.LENGTH_LONG).show()
    }

    private fun populateProductList(products: List<CartItem>) {
        val recyclerView: RecyclerView = findViewById(R.id.product_list_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ProductTrackAdapter(products)
    }


    private suspend fun emptyCart() {
        val cartReference = FirebaseDatabase.getInstance().reference.child("shoppingCart")

        try {
            cartReference.child("products").removeValue().await()
            // Cart emptied successfully
            loadShoppingCart()  // Refresh the UI after emptying the cart
            showToast("Shopping cart emptied")
        } catch (e: Exception) {
            // Failed to empty the cart
            showToast("Failed to empty shopping cart")
        }
    }

    private fun showClearCartConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Clear Shopping Cart")
        builder.setMessage("Are you sure you want to clear the shopping cart? This action cannot be undone.")

        builder.setPositiveButton("Clear") { _, _ ->
            GlobalScope.launch(Dispatchers.Main) {
                emptyCart()
            }
        }

        builder.setNegativeButton("Cancel") { _, _ ->
            // User canceled, do nothing
        }

        val dialog = builder.create()
        dialog.show()
    }

}
