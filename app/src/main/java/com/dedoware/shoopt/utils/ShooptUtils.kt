package com.dedoware.shoopt.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class ShooptUtils {

    companion object {
        private lateinit var firebaseDatabaseReference: DatabaseReference
        private lateinit var firebaseStorageReference: StorageReference

        fun getFirebaseDatabaseReference(): DatabaseReference {
            return firebaseDatabaseReference
        }

        fun getFirebaseStorageReference(): StorageReference {
            return firebaseStorageReference
        }

        fun doAfterInitFirebase(context: Context, doOnComplete: (() -> Unit)?) {
            val firebaseAuth = FirebaseAuth.getInstance()

            firebaseAuth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("SHOOPT_TAG", "signInAnonymously:success")

                        initializeFirebaseDatabase()
                        initializeFirebaseStorage()

                        doOnComplete?.let { it() }
                    } else {
                        Log.w("SHOOPT_TAG", "signInAnonymously:failure", task.exception)
                        Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        }

        private fun initializeFirebaseDatabase() {
            firebaseDatabaseReference =
                Firebase.database("https://shoopt-9ab47-default-rtdb.europe-west1.firebasedatabase.app/")
                    .reference
        }

        private fun initializeFirebaseStorage() {
            firebaseStorageReference = FirebaseStorage.getInstance().reference
        }
    }
}
