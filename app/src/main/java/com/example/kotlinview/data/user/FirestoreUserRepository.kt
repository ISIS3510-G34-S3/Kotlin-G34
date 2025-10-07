package com.example.kotlinview.data.user

import com.example.kotlinview.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreUserRepository(
    private val db: FirebaseFirestore
) : UserRepository {

    override suspend fun getByEmail(email: String): User? {
        if (email.isBlank()) return null
        val snap = db.collection("users").document(email).get().await()
        if (!snap.exists()) return null
        val d = snap.data ?: return null

        return User(
            email = email,
            displayName = (d["displayName"] as? String).orEmpty(),
            photoURL = (d["photoURL"] as? String).orEmpty(),
            provider = (d["provider"] as? String).orEmpty(),
            userType = (d["userType"] as? String).orEmpty(),
            createdAt = d["createdAt"] as? Timestamp,
            lastSignInAt = d["lastSignInAt"] as? Timestamp
        )
    }

    override suspend fun setLastSignInNow(email: String) {
        if (email.isBlank()) return
        db.collection("users")
            .document(email)
            .update(mapOf("lastSignInAt" to FieldValue.serverTimestamp()))
            .await()
    }
}
