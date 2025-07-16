package com.dedoware.shoopt.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.model.CartItem
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductTrackAdapter
import com.dedoware.shoopt.persistence.IShoppingListRepository
import com.dedoware.shoopt.persistence.FirebaseShoppingListRepository
import com.dedoware.shoopt.persistence.LocalShoppingListRepository
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList

class UpdateShoppingListActivity : AppCompatActivity() {
    private lateinit var mainShoppingListEditText: EditText
    private lateinit var secondaryShoppingListEditText: EditText
    private lateinit var backImageButton: ImageButton
    private lateinit var shoppingListRepository: IShoppingListRepository
    private lateinit var convertToProductTrackButton: ImageButton
    private lateinit var convertSecondaryToProductTrackButton: ImageButton
    private lateinit var productTrackRecyclerView: RecyclerView
    private lateinit var productTrackAdapter: ProductTrackAdapter
    private lateinit var secondaryProductTrackRecyclerView: RecyclerView
    private lateinit var secondaryProductTrackAdapter: ProductTrackAdapter
    private val shoppingItemList = mutableListOf<CartItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable { }

    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }

    private val useFirebase = false // This could be a config or user preference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_shopping_list)

        shoppingListRepository = if (useFirebase) {
            FirebaseShoppingListRepository()
        } else {
            LocalShoppingListRepository(
                database.shoppingListDao()
            )
        }

        supportActionBar?.hide()

        mainShoppingListEditText = findViewById(R.id.main_shopping_list_edit_text)
        secondaryShoppingListEditText = findViewById(R.id.secondary_shopping_list_edit_text)
        convertToProductTrackButton = findViewById(R.id.convert_to_product_track_IB)
        convertSecondaryToProductTrackButton = findViewById(R.id.convert_secondary_to_product_track_IB)
        productTrackRecyclerView = findViewById(R.id.product_track_recycler_view)
        productTrackAdapter = ProductTrackAdapter(shoppingItemList)
        productTrackRecyclerView.layoutManager = LinearLayoutManager(this)
        productTrackRecyclerView.adapter = productTrackAdapter

        secondaryProductTrackRecyclerView = findViewById(R.id.secondary_product_track_recycler_view)
        secondaryProductTrackAdapter = ProductTrackAdapter(mutableListOf())
        secondaryProductTrackRecyclerView.layoutManager = LinearLayoutManager(this)
        secondaryProductTrackRecyclerView.adapter = secondaryProductTrackAdapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                productTrackAdapter.removeAt(position)
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(productTrackRecyclerView)

        val secondaryItemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                secondaryProductTrackAdapter.removeAt(position)
            }
        }
        ItemTouchHelper(secondaryItemTouchHelperCallback).attachToRecyclerView(secondaryProductTrackRecyclerView)

        convertToProductTrackButton.setOnClickListener {
            val products = mainShoppingListEditText.text.toString()
                .split(Regex("[,.\\-;:|/]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (shoppingItemList.isEmpty()) {
                shoppingItemList.addAll(products.map {
                    val regex = Regex("(\\d+)")
                    val quantityMatch = regex.find(it)
                    val quantity = quantityMatch?.value?.toIntOrNull() ?: 1
                    val name = if (quantityMatch != null) it.replace(quantityMatch.value, "").trim() else it
                    Product(name = name, id = "", barcode = 0, timestamp = 0, price = 0.0, unitPrice = 0.0, shop = "", pictureUrl = "")
                        .let { product -> CartItem(product, quantity) }
                })
            } else {
                shoppingItemList.addAll(products.map {
                    val regex = Regex("(\\d+)")
                    val quantityMatch = regex.find(it)
                    val quantity = quantityMatch?.value?.toIntOrNull() ?: 1
                    val name = if (quantityMatch != null) it.replace(quantityMatch.value, "").trim() else it
                    Product(name = name, id = "", barcode = 0, timestamp = 0, price = 0.0, unitPrice = 0.0, shop = "", pictureUrl = "")
                        .let { product -> CartItem(product, quantity) }
                })
            }
            productTrackAdapter.notifyDataSetChanged()
            mainShoppingListEditText.setText("")
            Toast.makeText(this, getString(R.string.products_added, products.size), Toast.LENGTH_SHORT).show()
        }

        convertSecondaryToProductTrackButton.setOnClickListener {
            val products = secondaryShoppingListEditText.text.toString()
                .split(Regex("[,.\\-;:|/]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val newItems = products.map {
                val regex = Regex("(\\d+)")
                val quantityMatch = regex.find(it)
                val quantity = quantityMatch?.value?.toIntOrNull() ?: 1
                val name = if (quantityMatch != null) it.replace(quantityMatch.value, "").trim() else it
                Product(name = name, id = "", barcode = 0, timestamp = 0, price = 0.0, unitPrice = 0.0, shop = "", pictureUrl = "")
                    .let { product -> CartItem(product, quantity) }
            }
            val currentItems = secondaryProductTrackAdapter.getItems().toMutableList()
            currentItems.addAll(newItems)
            secondaryProductTrackAdapter.updateItems(currentItems)
            secondaryShoppingListEditText.setText("")
            Toast.makeText(this, getString(R.string.products_added, newItems.size), Toast.LENGTH_SHORT).show()
        }

        storeAndLoadShoppingList(mainShoppingListEditText, "mainShoppingList")
        storeAndLoadShoppingList(secondaryShoppingListEditText, "secondaryShoppingList")

        backImageButton = findViewById(R.id.back_IB)
        backImageButton.setOnClickListener {
            finish()
        }
    }

    private fun storeAndLoadShoppingList(shoppingListEditText: EditText, dbRefKey: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val shoppingList = withContext(Dispatchers.IO) {
                shoppingListRepository.getShoppingList(dbRefKey)
            }
            shoppingList?.let {
                val selectionStart = shoppingListEditText.selectionStart
                val selectionEnd = shoppingListEditText.selectionEnd
                shoppingListEditText.setText(it)
                shoppingListEditText.setSelection(selectionStart, selectionEnd)
            }

            shoppingListEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Do nothing
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    runnable.let { handler.removeCallbacks(it) }
                    runnable = Runnable {
                        val shoppingListContent = s.toString()
                        CoroutineScope(Dispatchers.IO).launch {
                            shoppingListRepository.saveShoppingList(dbRefKey, shoppingListContent)
                        }
                    }
                    handler.postDelayed(runnable, 500) // Delay the update by 500 milliseconds
                }

                override fun afterTextChanged(s: Editable?) {
                    // Do nothing
                }
            })
        }
    }

    private fun saveShoppingItemListToPreferences(prefKey: String, items: List<CartItem>) {
        val sharedPreferences = getSharedPreferences(prefKey, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val jsonList = Gson().toJson(items)
        editor.putString("shoppingItemList", jsonList)
        editor.apply()
    }

    private fun loadShoppingItemListFromPreferences(prefKey: String): List<CartItem> {
        val sharedPreferences = getSharedPreferences(prefKey, MODE_PRIVATE)
        val jsonList = sharedPreferences.getString("shoppingItemList", null)
        return if (jsonList != null) {
            val type = object : TypeToken<List<CartItem>>() {}.type
            Gson().fromJson(jsonList, type)
        } else {
            emptyList()
        }
    }

    override fun onPause() {
        super.onPause()
        saveShoppingItemListToPreferences("MainShoppingListPrefs", shoppingItemList)
        saveShoppingItemListToPreferences("SecondaryShoppingListPrefs", secondaryProductTrackAdapter.getItems())
    }

    override fun onResume() {
        super.onResume()
        shoppingItemList.clear()
        shoppingItemList.addAll(loadShoppingItemListFromPreferences("MainShoppingListPrefs"))
        productTrackAdapter.notifyDataSetChanged()

        secondaryProductTrackAdapter.updateItems(loadShoppingItemListFromPreferences("SecondaryShoppingListPrefs"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("shoppingItemList", ArrayList<Parcelable>(shoppingItemList))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredList = savedInstanceState.getParcelableArrayList<Parcelable>("shoppingItemList")?.filterIsInstance<CartItem>()
        if (restoredList != null) {
            shoppingItemList.clear()
            shoppingItemList.addAll(restoredList)
            productTrackAdapter.notifyDataSetChanged()
        }
    }
}