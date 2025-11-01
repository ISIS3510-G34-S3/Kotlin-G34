package com.example.kotlinview.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.kotlinview.core.SessionManager
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.data.map.ExperienceDtoMap
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

// Firestore para leer reviews (solo desde caché)
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.Date

data class FeedUiState(
    val loading: Boolean = false,
    val items: List<Experience> = emptyList(), // UI model de este paquete
    val error: Throwable? = null
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state

    // Banner de conectividad
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage

    private enum class Mode { FILTERED, RANDOM }

    private data class LoadParams(
        val mode: Mode,
        val limit: Int,
        val exclude: Set<String>,
        val department: String?,
        val startAtMs: Long?,
        val endAtMs: Long?,
        val onlyActive: Boolean
    )
    private var lastLoadParams: LoadParams? = null

    private var autoRetryJob: Job? = null

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    fun loadFeed(
        limit: Int = 20,
        department: String? = null,
        startAtMs: Long? = null,
        endAtMs: Long? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val emailLower = SessionManager.currentUser.value?.email?.trim()?.lowercase().orEmpty()
            val exclude = if (emailLower.isNotBlank()) setOf(emailLower) else emptySet()
            val repo = ServiceLocator.provideExperiencesRepository()

            val mode = if (department != null || (startAtMs != null && endAtMs != null)) {
                Mode.FILTERED
            } else {
                Mode.RANDOM
            }
            lastLoadParams = LoadParams(
                mode = mode,
                limit = limit,
                exclude = exclude,
                department = department,
                startAtMs = startAtMs,
                endAtMs = endAtMs,
                onlyActive = true
            )

            try {
                // 1) Traer DTOs con timeout 5 s
                val dtoList: List<ExperienceDtoMap> = withTimeoutOrNull(5_000L) {
                    when (mode) {
                        Mode.FILTERED -> repo.getFilteredFeed(
                            limit = limit,
                            excludeHostEmails = exclude,
                            department = department,
                            startAtMs = startAtMs,
                            endAtMs = endAtMs,
                            onlyActive = true
                        )
                        Mode.RANDOM -> repo.getRandomFeed(
                            limit = limit,
                            excludeHostIds = exclude,
                            onlyActive = true
                        )
                    }
                } ?: throw IOException("network timeout")

                // 2) PUBLICAR INMEDIATO (ratings N/A) → feed visible sin esperar
                val uiNa: List<Experience> = dtoList.map { it.toUiWithRating(null) }
                _state.value = _state.value.copy(loading = false, items = uiNa, error = null)
                if (uiNa.isNotEmpty()) {
                    _bannerMessage.value = null
                    stopAutoRetry()
                }

                // 3) Calcular ratings recientes (solo caché) en segundo plano (≤2 s) y refrescar
                viewModelScope.launch {
                    val threeMonthsMs = 90L * 24 * 60 * 60 * 1000
                    val sinceMs = System.currentTimeMillis() - threeMonthsMs

                    val updated: List<Experience>? = withTimeoutOrNull(2_000L) {
                        val recents = recentAveragesFromCache(dtoList.map { it.id }, sinceMs)
                        dtoList.map { dto -> dto.toUiWithRating(recents[dto.id]) }
                    }

                    if (updated != null && updated.isNotEmpty()) {
                        _state.value = _state.value.copy(items = updated)
                    }
                }

            } catch (e: Throwable) {
                Log.e("Feed", "loadFeed error", e)
                _state.value = _state.value.copy(loading = false, error = e)

                val noLocalData = _state.value.items.isEmpty()
                if (isNetworkError(e) && noLocalData) {
                    _bannerMessage.value =
                        "Experiences cannot be loaded due to a connection error. Please try again later."
                    startAutoRetry()
                } else {
                    _bannerMessage.value = null
                    stopAutoRetry()
                }
            }
        }
    }

    /* ===================== Auto-reintento cada 15 s (mismos parámetros) ===================== */

    private fun startAutoRetry() {
        if (autoRetryJob?.isActive == true) return
        val params = lastLoadParams ?: return

        autoRetryJob = viewModelScope.launch {
            while (_bannerMessage.value != null) {
                delay(15_000L)
                runCatching {
                    val repo = ServiceLocator.provideExperiencesRepository()
                    val dtoList: List<ExperienceDtoMap> = withTimeoutOrNull(5_000L) {
                        when (params.mode) {
                            Mode.FILTERED -> repo.getFilteredFeed(
                                limit = params.limit,
                                excludeHostEmails = params.exclude,
                                department = params.department,
                                startAtMs = params.startAtMs,
                                endAtMs = params.endAtMs,
                                onlyActive = params.onlyActive
                            )
                            Mode.RANDOM -> repo.getRandomFeed(
                                limit = params.limit,
                                excludeHostIds = params.exclude,
                                onlyActive = params.onlyActive
                            )
                        }
                    } ?: emptyList()

                    // Publicar inmediato sin ratings para no bloquear
                    val uiNa = dtoList.map { it.toUiWithRating(null) }

                    // Calcular ratings recientes (solo caché) en ≤2 s
                    val updated: List<Experience>? = withTimeoutOrNull(2_000L) {
                        val threeMonthsMs = 90L * 24 * 60 * 60 * 1000
                        val sinceMs = System.currentTimeMillis() - threeMonthsMs
                        val recents = recentAveragesFromCache(dtoList.map { it.id }, sinceMs)
                        dtoList.map { dto -> dto.toUiWithRating(recents[dto.id]) }
                    }

                    updated ?: uiNa
                }.onSuccess { uiList ->
                    if (uiList.isNotEmpty()) {
                        _state.value = _state.value.copy(loading = false, items = uiList, error = null)
                        _bannerMessage.value = null
                        stopAutoRetry()
                    }
                }.onFailure {
                    // seguimos intentando
                }
            }
        }
    }

    fun ensureAutoRetryIfEmpty() {
        if (_state.value.items.isEmpty()) {
            _bannerMessage.value =
                "Experiences cannot be loaded due to a connection error. Please try again later."
            startAutoRetry()
        }
    }

    fun triggerImmediateRetry() {
        val params = lastLoadParams ?: return
        viewModelScope.launch {
            runCatching {
                val repo = ServiceLocator.provideExperiencesRepository()
                val dtoList: List<ExperienceDtoMap> = withTimeoutOrNull(5_000L) {
                    when (params.mode) {
                        Mode.FILTERED -> repo.getFilteredFeed(
                            limit = params.limit,
                            excludeHostEmails = params.exclude,
                            department = params.department,
                            startAtMs = params.startAtMs,
                            endAtMs = params.endAtMs,
                            onlyActive = params.onlyActive
                        )
                        Mode.RANDOM -> repo.getRandomFeed(
                            limit = params.limit,
                            excludeHostIds = params.exclude,
                            onlyActive = params.onlyActive
                        )
                    }
                } ?: emptyList()

                val uiNa = dtoList.map { it.toUiWithRating(null) }

                val updated: List<Experience>? = withTimeoutOrNull(2_000L) {
                    val threeMonthsMs = 90L * 24 * 60 * 60 * 1000
                    val sinceMs = System.currentTimeMillis() - threeMonthsMs
                    val recents = recentAveragesFromCache(dtoList.map { it.id }, sinceMs)
                    dtoList.map { dto -> dto.toUiWithRating(recents[dto.id]) }
                }

                updated ?: uiNa
            }.onSuccess { uiList ->
                if (uiList.isNotEmpty()) {
                    _state.value = _state.value.copy(loading = false, items = uiList, error = null)
                    _bannerMessage.value = null
                    stopAutoRetry()
                }
            }
        }
    }

    private fun stopAutoRetry() {
        autoRetryJob?.cancel()
        autoRetryJob = null
    }

    /* ===================== Heurística de error de red ===================== */
    private fun isNetworkError(t: Throwable?): Boolean {
        if (t == null) return false
        return when (t) {
            is com.google.firebase.FirebaseNetworkException -> true
            is java.net.UnknownHostException -> true
            is java.net.SocketTimeoutException -> true
            is javax.net.ssl.SSLException -> true
            is java.io.IOException -> true
            else -> {
                val m = t.message?.lowercase() ?: return false
                "network" in m ||
                        "timeout" in m ||
                        "failed to connect" in m ||
                        "unable to resolve host" in m ||
                        "ssl" in m ||
                        "host unreachable" in m ||
                        "connection reset" in m ||
                        "connection refused" in m
            }
        }
    }

    /* ===================== Helpers de ratings (caché) ===================== */

    // Leer promedios recientes (últimos 90 días) para varios IDs desde caché
    private suspend fun recentAveragesFromCache(
        ids: List<String>,
        sinceMs: Long
    ): Map<String, Double?> {
        val result = HashMap<String, Double?>(ids.size)
        for (id in ids) {
            result[id] = recentAverageRatingFromCache(id, sinceMs)
        }
        return result
    }

    // Promedio de reviews de los últimos 3 meses para un ID, SOLO desde caché
    private suspend fun recentAverageRatingFromCache(experienceId: String, sinceMs: Long): Double? {
        val cutoff = Timestamp(Date(sinceMs))
        val ref = firestore.collection("experiences")
            .document(experienceId)
            .collection("reviews")

        val snap = runCatching { ref.limit(200).get(Source.CACHE).await() }.getOrNull() ?: return null
        if (snap.isEmpty) return null

        var sum = 0.0
        var count = 0

        for (doc in snap.documents) {
            val rating: Double? =
                doc.getDouble("rating")
                    ?: (doc.get("rating") as? Number)?.toDouble()
            if (rating == null) continue

            val ts: Timestamp? =
                (doc.get("createdAt") as? Timestamp)
                    ?: (doc.get("timestamp") as? Timestamp)
                    ?: (doc.get("updatedAt") as? Timestamp)
                    ?: (doc.get("date") as? Timestamp)

            if (ts != null && ts.seconds >= cutoff.seconds) {
                sum += rating
                count += 1
            }
        }
        return if (count > 0) sum / count else null
    }
}

/* ===================== Mapeos a UI ===================== */
private fun ExperienceDtoMap.toUi(): Experience =
    Experience(
        title        = this.title,
        rating       = this.avgRating,
        department   = this.department,
        reviewCount  = this.reviewsCount,
        duration     = this.duration,
        learnSkills  = this.skillsToLearn,
        teachSkills  = this.skillsToTeach,
        hostName     = this.hostName,
        imageUrl     = this.images.firstOrNull().orEmpty()
    )

// Con rating override (NaN = "N/A" en el adapter)
private fun ExperienceDtoMap.toUiWithRating(overrideRating: Double?): Experience =
    Experience(
        title        = this.title,
        rating       = overrideRating ?: Double.NaN,
        department   = this.department,
        reviewCount  = this.reviewsCount,
        duration     = this.duration,
        learnSkills  = this.skillsToLearn,
        teachSkills  = this.skillsToTeach,
        hostName     = this.hostName,
        imageUrl     = this.images.firstOrNull().orEmpty()
    )
