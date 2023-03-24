package com.dedoware.shoopt.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.dedoware.shoopt.R
import com.google.firebase.database.*

class UpdateShoppingListActivity : AppCompatActivity() {
    private lateinit var mainShoppingListEditText: EditText
    private lateinit var secondaryShoppingListEditText: EditText
    private lateinit var databaseReference: FirebaseDatabase
    private lateinit var mainShoppingListDbRef: DatabaseReference
    private lateinit var secondaryShoppingListDbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_shopping_list)

        supportActionBar?.hide()

        mainShoppingListEditText = findViewById(R.id.edit_text_shop1)
        secondaryShoppingListEditText = findViewById(R.id.edit_text_shop2)

        // Initialize Firebase database reference
        databaseReference = FirebaseDatabase.getInstance()

        mainShoppingListDbRef = databaseReference.getReference("mainShoppingList")
        secondaryShoppingListDbRef = databaseReference.getReference("secondaryShoppingList")

        storeShoppingListOnChange(mainShoppingListEditText, mainShoppingListDbRef)
        storeShoppingListOnChange(secondaryShoppingListEditText, secondaryShoppingListDbRef)

        loadShoppingList(mainShoppingListEditText, mainShoppingListDbRef)
        loadShoppingList(secondaryShoppingListEditText, secondaryShoppingListDbRef)
    }

    private fun loadShoppingList(
        shoppingListEditText: EditText,
        shoppingListDbRef: DatabaseReference
    ) {
        shoppingListDbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shoppingList1 = snapshot.getValue(String::class.java)
                shoppingListEditText.setText(shoppingList1)
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
                val mainShoppingListContent = s.toString()
                shoppingListDbRef.setValue(mainShoppingListContent)
            }

            override fun afterTextChanged(s: Editable?) {
                // Do nothing
            }
        })
    }
}