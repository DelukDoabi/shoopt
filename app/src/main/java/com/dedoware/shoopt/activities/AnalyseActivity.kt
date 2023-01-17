package com.dedoware.shoopt.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dedoware.shoopt.R
import com.dedoware.shoopt.model.Product
import com.dedoware.shoopt.model.ProductListAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class AnalyseActivity : AppCompatActivity() {
    private lateinit var productListRecyclerView: RecyclerView

    private lateinit var firebaseDatabaseReference: DatabaseReference
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressBar: ProgressBar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyse)

        progressBar = findViewById(R.id.loading_indicator)


        productListRecyclerView = findViewById(R.id.product_list_recycler_view)
        productListRecyclerView.layoutManager = LinearLayoutManager(this)
        productListRecyclerView.adapter = ProductListAdapter(emptyList())

        signInAnonymouslyToFirebasethenGetProducts()
    }

    private fun signInAnonymouslyToFirebasethenGetProducts() {
        firebaseAuth = FirebaseAuth.getInstance()

        firebaseAuth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("SHOOPT_TAG", "signInAnonymously:success")

                    initializeFirebaseDatabase()

                    getProductsFromRTDB()
                } else {
                    Log.w("SHOOPT_TAG", "signInAnonymously:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun getProductsFromRTDB() {
        progressBar.visibility = View.VISIBLE

        val productsReference =
            firebaseDatabaseReference.child("products")

        productsReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                progressBar.visibility = View.GONE

                val products = mutableListOf<Product>()
                dataSnapshot.children.forEach { productData ->
                    val product = productData.getValue(Product::class.java)
                    product?.let { products.add(it) }
                }
                if (products.size > 0) {
                    productListRecyclerView.adapter = ProductListAdapter(products)
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


    private fun initializeFirebaseDatabase() {
        firebaseDatabaseReference =
            Firebase.database("https://shoopt-9ab47-default-rtdb.europe-west1.firebasedatabase.app/")
                .reference
    }

}