package com.example.kotlinview.data.local.kv

import com.tencent.mmkv.MMKV

/**
 * Key/Value store for "nearest bucket" → top IDs.
 * Uses a single MMKV instance (process mode). No threads inside; callers are free to call on IO.
 *
 * Keys:
 *   "bucket:<bucketKey>:ids" -> comma-joined ids (String)
 *   "bucket:<bucketKey>:ts"  -> last update millis (Long)
 */
object MmkvBuckets {

    private const val IDS = "ids"
    private const val TS  = "ts"

    private val kv: MMKV by lazy {
        // Dedicated space for buckets
        MMKV.mmkvWithID("nearest_buckets")
            ?: throw IllegalStateException("MMKV not initialized")
    }

    private fun idsKey(bucketKey: String) = "bucket:$bucketKey:$IDS"
    private fun tsKey(bucketKey: String)  = "bucket:$bucketKey:$TS"

    suspend fun readTopIds(bucketKey: String): List<String> {
        val raw = kv.getString(idsKey(bucketKey), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(',').filter { it.isNotBlank() }
    }

    suspend fun writeTopIds(bucketKey: String, ids: List<String>, nowMs: Long) {
        val joined = ids.joinToString(",")
        kv.putString(idsKey(bucketKey), joined)
        kv.putLong(tsKey(bucketKey), nowMs)
    }

    // Optional extra accessor if you ever want it
    suspend fun readTimestamp(bucketKey: String): Long = kv.getLong(tsKey(bucketKey), 0L)
}
