package com.example.kotlinview.data.analytics

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface FeatureUsageRepository {
    suspend fun incrementMonthlyCount(featureKey: String, yearMonth: String)
}

class FirestoreFeatureUsageRepository(
    private val db: FirebaseFirestore
) : FeatureUsageRepository {

    override suspend fun incrementMonthlyCount(featureKey: String, yearMonth: String) {
        val docId = "${featureKey}_$yearMonth"
        val ref = db.collection("feature_usage_monthly").document(docId)

        // Transaction: create doc if missing, otherwise increment "count"
        db.runTransaction { tr ->
            val snap = tr.get(ref)
            val current = if (snap.exists()) (snap.getLong("count") ?: 0L) else 0L
            if (!snap.exists()) {
                tr.set(ref, mapOf(
                    "featureKey" to featureKey,
                    "date" to yearMonth,
                    "count" to current + 1
                ))
            } else {
                tr.update(ref,
                    mapOf(
                        "count" to (current + 1),
                        "featureKey" to featureKey,
                        "date" to yearMonth
                    )
                )
            }
            null
        }.await()
    }
}
