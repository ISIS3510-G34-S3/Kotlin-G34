package com.example.kotlinview.data.user

import com.example.kotlinview.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreUserRepository(
    private val db: FirebaseFirestore
) : UserRepository {

    override suspend fun getUser(uid: String): User? {
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        val data = snap.data ?: return null
        return User(
            uid = uid,
            displayName = (data["displayName"] as? String).orEmpty(),
            email = (data["email"] as? String).orEmpty(),
            interestedCategories = (data["interestedCategories"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            verified = (data["verified"] as? Boolean) ?: false
        )
    }
}
