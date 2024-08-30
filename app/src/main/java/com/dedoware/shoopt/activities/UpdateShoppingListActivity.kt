package com.dedoware.shoopt.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.dedoware.shoopt.ShooptApplication
import com.dedoware.shoopt.persistence.IShoppingListRepository
import com.dedoware.shoopt.persistence.FirebaseShoppingListRepository
import com.dedoware.shoopt.persistence.LocalShoppingListRepository
import com.dedoware.shoopt.persistence.ShooptRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateShoppingListActivity : AppCompatActivity() {
    private lateinit var mainShoppingListEditText: EditText
    private lateinit var secondaryShoppingListEditText: EditText
    private lateinit var backImageButton: ImageButton
    private lateinit var shoppingListRepository: IShoppingListRepository
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
}
