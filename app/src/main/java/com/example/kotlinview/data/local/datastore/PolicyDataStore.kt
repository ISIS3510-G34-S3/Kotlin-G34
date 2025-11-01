package com.example.kotlinview.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.policyDataStore by preferencesDataStore(name = "policy_meta")

object Keys {
    // Operational/meta
    val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
    val LAST_REMOTE_COUNT = longPreferencesKey("last_remote_count")
    val LAST_NEAREST_REFRESH_MS = longPreferencesKey("last_nearest_refresh_ms")
    val LAST_LOCATION_LAT = doublePreferencesKey("last_location_lat")
    val LAST_LOCATION_LNG = doublePreferencesKey("last_location_lng")

    // Policy knobs (kept simple)
    val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled") // default true
    val MOVE_DISTANCE_M = doublePreferencesKey("move_distance_m")            // default 250.0
    val REFRESH_MIN_INTERVAL_MS =
        longPreferencesKey("refresh_min_interval_ms")                        // default 10_000

    // (Optional legacy knob; safe to keep for compatibility, unused by the simple policy)
    val MOVE_TIME_MS = longPreferencesKey("move_time_ms")                    // default 20_000
}

data class PolicyMeta(
    // meta
    val lastSyncMs: Long = 0L,
    val lastRemoteCount: Long = 0L,
    val lastNearestRefreshMs: Long = 0L,
    val lastLocationLat: Double = 0.0,
    val lastLocationLng: Double = 0.0,

    // policy
    val autoRefreshEnabled: Boolean = true,
    val moveDistanceM: Double = 250.0,
    val refreshMinIntervalMs: Long = 10_000L,

    // legacy/optional (kept for compatibility; not required by the simple policy)
    val moveTimeMs: Long = 20_000L
)

class PolicyStore(private val ctx: Context) {

    suspend fun read(): PolicyMeta =
        ctx.policyDataStore.data.map { p ->
            PolicyMeta(
                lastSyncMs = p[Keys.LAST_SYNC_MS] ?: 0L,
                lastRemoteCount = p[Keys.LAST_REMOTE_COUNT] ?: 0L,
                lastNearestRefreshMs = p[Keys.LAST_NEAREST_REFRESH_MS] ?: 0L,
                lastLocationLat = p[Keys.LAST_LOCATION_LAT] ?: 0.0,
                lastLocationLng = p[Keys.LAST_LOCATION_LNG] ?: 0.0,
                autoRefreshEnabled = p[Keys.AUTO_REFRESH_ENABLED] ?: true,
                moveDistanceM = p[Keys.MOVE_DISTANCE_M] ?: 250.0,
                refreshMinIntervalMs = p[Keys.REFRESH_MIN_INTERVAL_MS] ?: 10_000L,
                moveTimeMs = p[Keys.MOVE_TIME_MS] ?: 20_000L
            )
        }.first()

    suspend fun update(mut: (PolicyMeta) -> PolicyMeta) {
        val current = read()
        val next = mut(current)
        ctx.policyDataStore.edit { p ->
            // meta
            p[Keys.LAST_SYNC_MS] = next.lastSyncMs
            p[Keys.LAST_REMOTE_COUNT] = next.lastRemoteCount
            p[Keys.LAST_NEAREST_REFRESH_MS] = next.lastNearestRefreshMs
            p[Keys.LAST_LOCATION_LAT] = next.lastLocationLat
            p[Keys.LAST_LOCATION_LNG] = next.lastLocationLng

            // policy
            p[Keys.AUTO_REFRESH_ENABLED] = next.autoRefreshEnabled
            p[Keys.MOVE_DISTANCE_M] = next.moveDistanceM
            p[Keys.REFRESH_MIN_INTERVAL_MS] = next.refreshMinIntervalMs

            // legacy/optional
            p[Keys.MOVE_TIME_MS] = next.moveTimeMs
        }
    }
}
