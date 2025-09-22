package com.dedoware.shoopt.activities

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.widget.CheckBox
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.dedoware.shoopt.utils.AnalyticsManager
import com.dedoware.shoopt.utils.CrashlyticsManager
import com.dedoware.shoopt.utils.UserPreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
// Imports pour le système de spotlight
import com.dedoware.shoopt.extensions.startSpotlightTour
import com.dedoware.shoopt.extensions.createSpotlightItem
import com.dedoware.shoopt.extensions.isSpotlightAvailable
import com.dedoware.shoopt.models.SpotlightShape
import com.google.android.material.card.MaterialCardView

class UpdateShoppingListActivity : AppCompatActivity() {
    private lateinit var mainShoppingListEditText: TextInputEditText
    private lateinit var backButton: MaterialButton
    private lateinit var shoppingListRepository: IShoppingListRepository
    private lateinit var convertToProductTrackButton: MaterialButton
    private lateinit var productsListCardMaterialCardView: MaterialCardView
    private lateinit var emptyMainListButton: MaterialButton
    private lateinit var productTrackRecyclerView: RecyclerView
    private lateinit var productTrackAdapter: ProductTrackAdapter
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var productCountBadge: TextView
    private lateinit var pasteFab: ExtendedFloatingActionButton
    private lateinit var fullscreenZoomButton: MaterialButton
    private val shoppingItemList = mutableListOf<CartItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable { }

    // Modern ActivityResultLauncher for fullscreen activity
    private lateinit var fullscreenLauncher: ActivityResultLauncher<Intent>

    // Request code for fullscreen activity
    companion object {
        private const val REQUEST_CODE_FULLSCREEN = 1001
    }

    // Variables pour le suivi analytique
    private var sessionStartTime: Long = 0
    private var textEditCount: Int = 0
    private var conversionCount: Int = 0

    private val database: ShooptRoomDatabase by lazy {
        (application as ShooptApplication).database
    }

    private val useFirebase = false // This could be a config or user preference

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_update_shopping_list)

            // Initialize the modern ActivityResultLauncher
            setupFullscreenActivityLauncher()

            // Enregistrer la vue d'écran dans Analytics
            AnalyticsManager.logScreenView("UpdateShoppingList", this::class.java.simpleName)

            // Initialiser le suivi analytique
            sessionStartTime = System.currentTimeMillis()
            AnalyticsManager.logEvent("shopping_list_activity_open", null)

            try {
                shoppingListRepository = if (useFirebase) {
                    FirebaseShoppingListRepository()
                } else {
                    LocalShoppingListRepository(
                        database.shoppingListDao()
                    )
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation du repository de listes de courses: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "shopping_list_repository_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, getString(R.string.app_loading_error), Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            try {
                mainShoppingListEditText = findViewById(R.id.main_shopping_list_edit_text)
                convertToProductTrackButton = findViewById(R.id.convert_to_product_track_IB)
                productsListCardMaterialCardView = findViewById(R.id.products_list_card)
                productTrackRecyclerView = findViewById(R.id.product_track_recycler_view)
                emptyStateContainer = findViewById(R.id.empty_state_container)
                productCountBadge = findViewById(R.id.product_count_badge)
                pasteFab = findViewById(R.id.paste_fab)
                fullscreenZoomButton = findViewById(R.id.fullscreen_zoom_button)

                productTrackAdapter = ProductTrackAdapter(shoppingItemList)
                productTrackRecyclerView.layoutManager = LinearLayoutManager(this)
                productTrackRecyclerView.adapter = productTrackAdapter

                // Initial empty state update
                updateEmptyState()

                // Setup paste FAB functionality
                setupPasteFunctionality()

                // Setup fullscreen zoom functionality
                setupFullscreenZoomFunctionality()

                // Show quick tips dialog on focus if enabled (reuse dialog_photo_tips layout)
                mainShoppingListEditText.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        try {
                            if (UserPreferences.shouldShowQuickTips(this)) {
                                showQuickTipsDialog()
                            }
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors de l'affichage des quick tips: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.logException(e)
                        }
                    }
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation des composants d'interface: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "ui_components_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, getString(R.string.dialog_display_error), Toast.LENGTH_SHORT).show()
            }

            try {
                val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        try {
                            val position = viewHolder.adapterPosition
                            val itemName = if (position >= 0 && position < shoppingItemList.size) {
                                shoppingItemList[position].product.name
                            } else {
                                "inconnu"
                            }
                            productTrackAdapter.removeAt(position)
                            updateEmptyState() // Update empty state after removal

                            // Save immediately after removal to preserve state
                            saveShoppingItemListToPreferences("MainShoppingListPrefs", shoppingItemList)

                            // Analytics pour le swipe d'un élément
                            val params = Bundle().apply {
                                putString("direction", if (direction == ItemTouchHelper.LEFT) "gauche" else "droite")
                                putInt("position", position)
                                putInt("items_restants", shoppingItemList.size)
                            }
                            AnalyticsManager.logEvent("shopping_item_deleted", params)
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors du swipe pour suppression: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.setCustomKey("error_location", "swipe_item_removal")
                            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                            CrashlyticsManager.logException(e)

                            Toast.makeText(this@UpdateShoppingListActivity, getString(R.string.failed_to_delete_product), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(productTrackRecyclerView)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de la configuration du swipe pour suppression: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "swipe_config")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)
            }

            convertToProductTrackButton.setOnClickListener {
                try {
                    val products = mainShoppingListEditText.text.toString()
                        .split(Regex("[,.\\-;:|/]+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    if (products.isNotEmpty()) {
                        val newProducts = products.map {
                            val regex = Regex("(\\d+)")
                            val quantityMatch = regex.find(it)
                            val quantity = quantityMatch?.value?.toIntOrNull() ?: 1
                            val name = if (quantityMatch != null) it.replace(quantityMatch.value, "").trim() else it
                            Product(name = name, id = "", barcode = 0, timestamp = 0, price = 0.0, unitPrice = 0.0, shop = "", pictureUrl = "")
                                .let { product -> CartItem(product, quantity) }
                        }

                        shoppingItemList.addAll(newProducts)
                        productTrackAdapter.notifyDataSetChanged()
                        updateEmptyState() // Update empty state after adding products

                        // Save immediately after conversion to preserve state
                        saveShoppingItemListToPreferences("MainShoppingListPrefs", shoppingItemList)

                        mainShoppingListEditText.setText("")
                        Toast.makeText(this, getString(R.string.products_added, products.size), Toast.LENGTH_SHORT).show()

                        // Suivi de la conversion de la liste
                        conversionCount++
                        AnalyticsManager.logEvent("shopping_list_converted", null)
                    }
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de la conversion de la liste: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "convert_shopping_list")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, getString(R.string.error_adding_products), Toast.LENGTH_SHORT).show()
                }
            }

            // Récupérer et afficher la liste de courses principale au démarrage
            loadShoppingItemListFromPreferences("MainShoppingListPrefs", shoppingItemList)

            // Initialize missing button listeners that were removed
            emptyMainListButton = findViewById(R.id.empty_main_list_IB)
            emptyMainListButton.setOnClickListener {
                try {
                    showConfirmationDialog {
                        try {
                            shoppingItemList.clear()
                            productTrackAdapter.notifyDataSetChanged()
                            updateEmptyState() // Update empty state after clearing
                            mainShoppingListEditText.setText("")

                            // Aussi sauvegarder la liste vide dans le repository
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    shoppingListRepository.saveShoppingList("mainShoppingList", "")
                                } catch (e: Exception) {
                                    CrashlyticsManager.log("Erreur lors de la sauvegarde de la liste vide: ${e.message ?: "Message non disponible"}")
                                    CrashlyticsManager.setCustomKey("error_location", "save_empty_list")
                                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                                    CrashlyticsManager.logException(e)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@UpdateShoppingListActivity, getString(R.string.failed_to_empty_shopping_cart), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            Toast.makeText(this, getString(R.string.shopping_cart_emptied), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors de la vidange de la liste: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.setCustomKey("error_location", "empty_list_action")
                            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                            CrashlyticsManager.logException(e)

                            Toast.makeText(this, getString(R.string.unexpected_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de confirmation: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "show_confirmation_dialog")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, getString(R.string.dialog_display_error), Toast.LENGTH_SHORT).show()
                }
            }

            try {
                storeAndLoadShoppingList(mainShoppingListEditText, "mainShoppingList")
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du chargement initial de la liste de courses: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "initial_list_load")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, getString(R.string.shopping_list_open_error), Toast.LENGTH_SHORT).show()
            }

            backButton = findViewById(R.id.back_IB)
            backButton.setOnClickListener {
                finish()
            }

            // Démarrer le système de spotlight si nécessaire
            setupSpotlightTour()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur générale dans onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, getString(R.string.app_loading_error), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showQuickTipsDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_photo_tips, null)
            // Update title/message to correspond to quick tips
            val titleView = dialogView.findViewById<TextView>(R.id.photo_tips_title)
            val messageView = dialogView.findViewById<TextView>(R.id.photo_tips_message)
            titleView?.setText(R.string.shopping_list_tips_title)
            messageView?.setText(R.string.shopping_list_tips_content)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_background)

            val continueButton = dialogView.findViewById<MaterialButton>(R.id.continue_button)
            val dontShowAgainCheckbox = dialogView.findViewById<CheckBox>(R.id.dont_show_again_checkbox)

            continueButton.setOnClickListener {
                if (dontShowAgainCheckbox?.isChecked == true) {
                    UserPreferences.setQuickTipsEnabled(this, false)
                }
                dialog.dismiss()
            }

            dialog.show()
            AnalyticsManager.logCustomEvent("quick_tips_dialog_shown", null)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de l'affichage du quick tips dialog: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }

    private fun showConfirmationDialog(onConfirm: () -> Unit) {
        // Analyser l'affichage du dialogue
        val showDialogParams = Bundle().apply {
            putInt("items_count", shoppingItemList.size)
        }
        AnalyticsManager.logEvent("empty_list_dialog_shown", showDialogParams)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_shopping_cart))
            .setMessage(getString(R.string.clear_shopping_cart_confirm))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                // Analyser la confirmation du vidage
                val confirmParams = Bundle().apply {
                    putInt("items_removed", shoppingItemList.size)
                }
                AnalyticsManager.logEvent("empty_list_confirmed", confirmParams)
                onConfirm()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                // Analyser l'annulation du vidage
                AnalyticsManager.logEvent("empty_list_cancelled", null)
            }
            .show()
    }

    private fun setupPasteFunctionality() {
        pasteFab.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip

                if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val pastedText = item?.text?.toString()

                    if (!pastedText.isNullOrBlank()) {
                        // Add to text input instead of directly to converted products
                        val currentText = mainShoppingListEditText.text.toString().trim()
                        val newText = if (currentText.isEmpty()) {
                            pastedText.trim()
                        } else {
                            "$currentText\n${pastedText.trim()}"
                        }

                        mainShoppingListEditText.setText(newText)
                        mainShoppingListEditText.setSelection(newText.length) // Move cursor to end

                        // Analytics for paste action
                        val pasteParams = Bundle().apply {
                            putInt("pasted_length", pastedText.length)
                            putInt("current_text_length", currentText.length)
                            putBoolean("had_existing_text", currentText.isNotEmpty())
                        }
                        AnalyticsManager.logEvent("shopping_list_pasted", pasteParams)

                        Toast.makeText(this, getString(R.string.paste_successful), Toast.LENGTH_SHORT).show()

                        // Clear clipboard after successful paste to prevent reuse
                        try {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                        } catch (e: Exception) {
                            // Ignore clipboard clearing errors
                        }

                        // Update FAB visibility after successful paste
                        updatePasteFabVisibility()

                        // Optionally vibrate for haptic feedback (if permission available)
                        try {
                            @Suppress("DEPRECATION")
                            (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(50)
                        } catch (e: Exception) {
                            // Ignore vibration errors
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors du collage: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "paste_functionality")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, getString(R.string.paste_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFullscreenZoomFunctionality() {
        fullscreenZoomButton.setOnClickListener {
            try {
                // Use JSON serialization instead of Parcelable to avoid corruption issues
                val gson = Gson()
                val productsJson = gson.toJson(shoppingItemList)

                val intent = Intent(this, ProductsFullscreenActivity::class.java).apply {
                    putExtra(ProductsFullscreenActivity.EXTRA_PRODUCTS, productsJson)
                }
                fullscreenLauncher.launch(intent)

                // Add smooth transition animation
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

                // Analytics for fullscreen zoom action
                val zoomParams = Bundle().apply {
                    putInt("items_count", shoppingItemList.size)
                }
                AnalyticsManager.logEvent("products_fullscreen_opened", zoomParams)
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'ouverture du mode plein écran: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "fullscreen_zoom")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Error opening fullscreen view", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFullscreenActivityLauncher() {
        fullscreenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    try {
                        // Get the updated products JSON from the result
                        val updatedProductsJson = data.getStringExtra(ProductsFullscreenActivity.RESULT_PRODUCTS_UPDATED)

                        if (!updatedProductsJson.isNullOrEmpty()) {
                            // Deserialize the updated product list
                            val gson = Gson()
                            val type = object : TypeToken<List<CartItem>>() {}.type
                            val updatedProducts: List<CartItem> = gson.fromJson(updatedProductsJson, type)

                            // Clear current list and add updated products
                            shoppingItemList.clear()
                            shoppingItemList.addAll(updatedProducts)

                            // Notify adapter of changes
                            productTrackAdapter.notifyDataSetChanged()

                            // Update UI state
                            updateEmptyState()

                            // Save updated list to preferences
                            saveShoppingItemListToPreferences("MainShoppingListPrefs", shoppingItemList)

                            // Log analytics for successful sync
                            val syncParams = Bundle().apply {
                                putInt("updated_items_count", shoppingItemList.size)
                            }
                            AnalyticsManager.logEvent("products_fullscreen_sync_success", syncParams)
                        }
                    } catch (e: Exception) {
                        CrashlyticsManager.log("Erreur lors de la synchronisation des produits depuis le mode plein écran: ${e.message ?: "Message non disponible"}")
                        CrashlyticsManager.setCustomKey("error_location", "fullscreen_result_sync")
                        CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                        CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                        CrashlyticsManager.logException(e)

                        Toast.makeText(this, "Error syncing product changes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Check clipboard content when window gains focus (returning from other apps)
            updatePasteFabVisibility()
        }
    }

    /**
     * Updates the visibility of the paste FAB based on clipboard content
     */
    private fun updatePasteFabVisibility() {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val hasClipboardContent = clipboardManager.hasPrimaryClip() &&
                clipboardManager.primaryClip?.itemCount ?: 0 > 0 &&
                !clipboardManager.primaryClip?.getItemAt(0)?.text.isNullOrBlank()

            // Show/hide FAB with smooth animation for better UX
            if (hasClipboardContent) {
                if (pasteFab.visibility != View.VISIBLE) {
                    pasteFab.visibility = View.VISIBLE
                    pasteFab.alpha = 0f
                    pasteFab.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                }
            } else {
                if (pasteFab.visibility == View.VISIBLE) {
                    pasteFab.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            pasteFab.visibility = View.GONE
                        }
                        .start()
                }
            }
        } catch (e: Exception) {
            // If we can't check clipboard, hide FAB by default
            pasteFab.visibility = View.GONE
            CrashlyticsManager.log("Erreur lors de la vérification du presse-papiers: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }

    private fun updateEmptyState() {
        try {
            val isEmpty = shoppingItemList.isEmpty()

            // Toggle visibility between empty state and RecyclerView
            emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
            productTrackRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

            // Update product count badge
            productCountBadge.text = shoppingItemList.size.toString()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la mise à jour de l'état vide: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }

    private fun saveShoppingItemListToPreferences(prefsName: String, itemList: List<CartItem>) {
        try {
            val sharedPreferences = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val gson = Gson()
            val json = gson.toJson(itemList)
            editor.putString("shopping_list", json)
            editor.apply()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la sauvegarde de la liste de courses: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "save_shopping_list")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
        }
    }

    private fun loadShoppingItemListFromPreferences(prefsName: String, itemList: MutableList<CartItem>) {
        try {
            val sharedPreferences = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val gson = Gson()
            val json = sharedPreferences.getString("shopping_list", null)

            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<CartItem>>() {}.type
                val loadedList: List<CartItem> = gson.fromJson(json, type)
                itemList.clear()
                itemList.addAll(loadedList)
                productTrackAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du chargement de la liste de courses: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "load_shopping_list")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
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
                    handler.removeCallbacks(runnable)
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

    override fun onPause() {
        try {
            super.onPause()
            saveShoppingItemListToPreferences("MainShoppingListPrefs", shoppingItemList)

            // Mesure de la durée d'utilisation de l'écran et enregistrement des statistiques
            val sessionDuration = System.currentTimeMillis() - sessionStartTime
            val sessionParams = Bundle().apply {
                putLong("duration_ms", sessionDuration)
                putInt("items_count", shoppingItemList.size)
                putInt("text_edit_count", textEditCount)
                putInt("conversion_count", conversionCount)
            }
            AnalyticsManager.logEvent("shopping_list_session", sessionParams)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la pause de l'activité: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "on_pause_activity")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            // Réinitialiser les compteurs d'analyse pour cette session
            sessionStartTime = System.currentTimeMillis()
            textEditCount = 0
            conversionCount = 0

            // Recharger la liste de courses principale à chaque reprise de l'activité
            loadShoppingItemListFromPreferences("MainShoppingListPrefs", shoppingItemList)

            // Update paste FAB visibility when returning to screen
            updatePasteFabVisibility()

            // Enregistrer la reprise de l'activité
            val resumeParams = Bundle().apply {
                putInt("items_loaded", shoppingItemList.size)
            }
            AnalyticsManager.logEvent("shopping_list_resumed", resumeParams)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la reprise de l'activité: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "on_resume_activity")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, getString(R.string.shopping_list_open_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        try {
            super.onSaveInstanceState(outState)
            outState.putParcelableArrayList("shoppingItemList", ArrayList<Parcelable>(shoppingItemList))
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la sauvegarde de l'état de l'instance: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "save_instance_state")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        try {
            super.onRestoreInstanceState(savedInstanceState)
            val restoredList = savedInstanceState.getParcelableArrayList<Parcelable>("shoppingItemList")?.filterIsInstance<CartItem>()
            if (restoredList != null) {
                shoppingItemList.clear()
                shoppingItemList.addAll(restoredList)
                productTrackAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la restauration de l'état de l'instance: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "restore_instance_state")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, getString(R.string.shopping_list_open_error), Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Configure et démarre le tour de spotlight pour guider l'utilisateur
     * sur les fonctionnalités principales de l'écran de mise à jour de liste de courses
     */
    private fun setupSpotlightTour() {
        try {
            // Vérifier si le spotlight doit être affiché
            if (!isSpotlightAvailable()) {
                return
            }

            // Créer la liste des éléments à mettre en surbrillance
            val spotlightItems = mutableListOf<com.dedoware.shoopt.models.SpotlightItem>()

            // Spotlight pour le champ de saisie de la liste de courses
            spotlightItems.add(
                createSpotlightItem(
                    targetView = mainShoppingListEditText,
                    titleRes = R.string.spotlight_list_input_title,
                    descriptionRes = R.string.spotlight_list_input_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le bouton de conversion
            spotlightItems.add(
                createSpotlightItem(
                    targetView = convertToProductTrackButton,
                    titleRes = R.string.spotlight_list_convert_title,
                    descriptionRes = R.string.spotlight_list_convert_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le champ d'affichage de la liste de produits organisés
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productsListCardMaterialCardView,
                    titleRes = R.string.spotlight_list_converted_title,
                    descriptionRes = R.string.spotlight_list_converted_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le compteur de produits
            spotlightItems.add(
                createSpotlightItem(
                    targetView = productCountBadge,
                    titleRes = R.string.spotlight_list_count_title,
                    descriptionRes = R.string.spotlight_list_count_description,
                    shape = SpotlightShape.CIRCLE
                )
            )

            // Spotlight pour le bouton plein écran (toujours affiché)
            spotlightItems.add(
                createSpotlightItem(
                    targetView = fullscreenZoomButton,
                    titleRes = R.string.spotlight_list_fullscreen_title,
                    descriptionRes = R.string.spotlight_list_fullscreen_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Spotlight pour le bouton de vidage (toujours affiché)
            spotlightItems.add(
                createSpotlightItem(
                    targetView = emptyMainListButton,
                    titleRes = R.string.spotlight_list_clear_title,
                    descriptionRes = R.string.spotlight_list_clear_description,
                    shape = SpotlightShape.ROUNDED_RECTANGLE
                )
            )

            // Démarrer le tour de spotlight avec un léger délai pour que l'interface soit prête
            window.decorView.post {
                startSpotlightTour(spotlightItems) {
                    // Callback appelé à la fin du tour
                    AnalyticsManager.logUserAction(
                        "spotlight_tour_completed",
                        "onboarding",
                        mapOf("screen" to "UpdateShoppingListActivity")
                    )
                }
            }

        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la configuration du spotlight: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "setup_spotlight_tour")
            CrashlyticsManager.logException(e)
        }
    }
    override fun onDestroy() {
        try {
            super.onDestroy()
            // Sauvegarder la liste de courses principale lors de la destruction de l'activité
            saveShoppingItemListToPreferences("MainShoppingListPrefs", shoppingItemList)
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la destruction de l'activité: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.logException(e)
        }
    }
}
