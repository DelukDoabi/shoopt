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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UpdateShoppingListActivity : AppCompatActivity() {
    private lateinit var mainShoppingListEditText: EditText
    private lateinit var secondaryShoppingListEditText: EditText
    private lateinit var databaseReference: FirebaseDatabase
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable { }
    private lateinit var backImageButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_shopping_list)

        supportActionBar?.hide()

        mainShoppingListEditText = findViewById(R.id.main_shopping_list_edit_text)
        secondaryShoppingListEditText = findViewById(R.id.secondary_shopping_list_edit_text)

        // Initialize Firebase database reference
        databaseReference = FirebaseDatabase.getInstance()

        storeAndLoadShoppingList(mainShoppingListEditText, "mainShoppingList")
        storeAndLoadShoppingList(secondaryShoppingListEditText, "secondaryShoppingList")

        backImageButton = findViewById(R.id.back_IB)

        backImageButton.setOnClickListener {
            finish()
        }
    }

    private fun loadShoppingList(
        shoppingListEditText: EditText,
        shoppingListDbRef: DatabaseReference
    ) {
        shoppingListDbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shoppingList = snapshot.getValue(String::class.java)
                val selectionStart = shoppingListEditText.selectionStart
                val selectionEnd = shoppingListEditText.selectionEnd
                shoppingListEditText.setText(shoppingList)
                shoppingListEditText.setSelection(selectionStart, selectionEnd)
            }

            override fun onCancelled(error: DatabaseError) {
                // Do nothing
            }
        })
    }


    private fun storeShoppingListOnChange(
        shoppingListEditText: EditText,
        shoppingListDbRef: DatabaseReference
    ) {
        shoppingListEditText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runnable.let { handler.removeCallbacks(it) }
                runnable = Runnable {
                    val shoppingListContent = s.toString()
                    shoppingListDbRef.setValue(shoppingListContent)
                }
                handler.postDelayed(runnable, 500) // Delay the update by 500 milliseconds
            }

            override fun afterTextChanged(s: Editable?) {
                // Do nothing
            }
        })
    }

    private fun storeAndLoadShoppingList(
        shoppingListEditText: EditText,
        dbRefKey: String
    ) {
        val shoppingListDbRef = databaseReference.getReference(dbRefKey)
        loadShoppingList(shoppingListEditText, shoppingListDbRef)
        storeShoppingListOnChange(shoppingListEditText, shoppingListDbRef)
    }
}
