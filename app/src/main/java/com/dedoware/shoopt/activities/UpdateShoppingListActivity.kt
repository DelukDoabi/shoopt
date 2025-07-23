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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList

class UpdateShoppingListActivity : AppCompatActivity() {
    private lateinit var mainShoppingListEditText: EditText
    private lateinit var backImageButton: ImageButton
    private lateinit var shoppingListRepository: IShoppingListRepository
    private lateinit var convertToProductTrackButton: ImageButton
    private lateinit var emptyMainListButton: ImageButton
    private lateinit var productTrackRecyclerView: RecyclerView
    private lateinit var productTrackAdapter: ProductTrackAdapter
    private val shoppingItemList = mutableListOf<CartItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable { }

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

                Toast.makeText(this, "Erreur lors de l'initialisation de l'application", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            supportActionBar?.hide()

            try {
                mainShoppingListEditText = findViewById(R.id.main_shopping_list_edit_text)
                convertToProductTrackButton = findViewById(R.id.convert_to_product_track_IB)
                productTrackRecyclerView = findViewById(R.id.product_track_recycler_view)
                productTrackAdapter = ProductTrackAdapter(shoppingItemList)
                productTrackRecyclerView.layoutManager = LinearLayoutManager(this)
                productTrackRecyclerView.adapter = productTrackAdapter
            } catch (e: Exception) {
                CrashlyticsManager.log("Erreur lors de l'initialisation des composants d'interface: ${e.message ?: "Message non disponible"}")
                CrashlyticsManager.setCustomKey("error_location", "ui_components_init")
                CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                CrashlyticsManager.logException(e)

                Toast.makeText(this, "Erreur lors de l'initialisation de l'interface", Toast.LENGTH_SHORT).show()
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

                            Toast.makeText(this@UpdateShoppingListActivity, "Erreur lors de la suppression de l'élément", Toast.LENGTH_SHORT).show()
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

                    // Suivi de la conversion de la liste
                    conversionCount++
                    AnalyticsManager.logEvent("shopping_list_converted", null)
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de la conversion de la liste de courses: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "convert_list")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Erreur lors de la conversion de la liste", Toast.LENGTH_SHORT).show()
                }
            }

            emptyMainListButton = findViewById(R.id.empty_main_list_IB)
            emptyMainListButton.setOnClickListener {
                try {
                    showConfirmationDialog {
                        try {
                            shoppingItemList.clear()
                            productTrackAdapter.notifyDataSetChanged()
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
                                        Toast.makeText(this@UpdateShoppingListActivity, "Erreur lors de la sauvegarde de la liste", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            Toast.makeText(this, R.string.shopping_cart_emptied, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            CrashlyticsManager.log("Erreur lors de la vidange de la liste: ${e.message ?: "Message non disponible"}")
                            CrashlyticsManager.setCustomKey("error_location", "empty_list_action")
                            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                            CrashlyticsManager.logException(e)

                            Toast.makeText(this, "Erreur lors de la suppression de la liste", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    CrashlyticsManager.log("Erreur lors de l'affichage du dialogue de confirmation: ${e.message ?: "Message non disponible"}")
                    CrashlyticsManager.setCustomKey("error_location", "show_confirmation_dialog")
                    CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
                    CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
                    CrashlyticsManager.logException(e)

                    Toast.makeText(this, "Erreur lors de l'affichage du dialogue", Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this, "Erreur lors du chargement de la liste", Toast.LENGTH_SHORT).show()
            }

            backImageButton = findViewById(R.id.back_IB)
            backImageButton.setOnClickListener {
                finish()
            }
        } catch (e: Exception) {
            // Capture des erreurs globales dans onCreate
            CrashlyticsManager.log("Erreur globale dans UpdateShoppingListActivity.onCreate: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "shopping_list_activity_init")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Une erreur est survenue lors du démarrage de l'application", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showConfirmationDialog(onConfirm: () -> Unit) {
        // Analyser l'affichage du dialogue
        val showDialogParams = Bundle().apply {
            putInt("items_count", shoppingItemList.size)
        }
        AnalyticsManager.logEvent("empty_list_dialog_shown", showDialogParams)

        AlertDialog.Builder(this)
            .setTitle(R.string.clear_shopping_cart)
            .setMessage(R.string.clear_shopping_cart_confirm)
            .setPositiveButton(R.string.clear) { _, _ ->
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
        try {
            val sharedPreferences = getSharedPreferences(prefKey, MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val jsonList = Gson().toJson(items)
            editor.putString("shoppingItemList", jsonList)
            editor.apply()
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la sauvegarde de la liste dans les préférences: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "save_shopping_list_preferences")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
        }
    }

    private fun loadShoppingItemListFromPreferences(prefKey: String): List<CartItem> {
        try {
            val sharedPreferences = getSharedPreferences(prefKey, MODE_PRIVATE)
            val jsonList = sharedPreferences.getString("shoppingItemList", null)
            return if (jsonList != null) {
                val type = object : TypeToken<List<CartItem>>() {}.type
                Gson().fromJson(jsonList, type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors du chargement de la liste depuis les préférences: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "load_shopping_list_preferences")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)
            return emptyList()
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

            shoppingItemList.clear()
            shoppingItemList.addAll(loadShoppingItemListFromPreferences("MainShoppingListPrefs"))
            productTrackAdapter.notifyDataSetChanged()

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

            Toast.makeText(this, "Erreur lors du chargement de la liste de courses", Toast.LENGTH_SHORT).show()
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
            }
        } catch (e: Exception) {
            CrashlyticsManager.log("Erreur lors de la restauration de l'état de l'instance: ${e.message ?: "Message non disponible"}")
            CrashlyticsManager.setCustomKey("error_location", "restore_instance_state")
            CrashlyticsManager.setCustomKey("exception_class", e.javaClass.name)
            CrashlyticsManager.setCustomKey("exception_message", e.message ?: "Message non disponible")
            CrashlyticsManager.logException(e)

            Toast.makeText(this, "Erreur lors de la restauration de l'état de l'application", Toast.LENGTH_SHORT).show()
        }
    }
}