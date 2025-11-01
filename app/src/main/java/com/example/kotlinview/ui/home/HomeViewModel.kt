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

data class FeedUiState(
    val loading: Boolean = false,
    val items: List<Experience> = emptyList(), // UI model de este paquete
    val error: Throwable? = null
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state

    // Mensaje de banner para errores de conectividad SIN datos locales
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage

    // Modo de carga para recordar cómo reintentar con exactamente los mismos parámetros
    private enum class Mode { FILTERED, RANDOM }

    // Últimos parámetros usados para el fetch (para reintento automático)
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

    // Job de reintento automático
    private var autoRetryJob: Job? = null

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
            // Guardar parámetros reales para reintento
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
                val uiList: List<Experience> = withTimeoutOrNull(5_000L) {
                    when (mode) {
                        Mode.FILTERED -> {
                            repo.getFilteredFeed(
                                limit = limit,
                                excludeHostEmails = exclude,
                                department = department,
                                startAtMs = startAtMs,
                                endAtMs = endAtMs,
                                onlyActive = true
                            ).map { it.toUi() }
                        }
                        Mode.RANDOM -> {
                            repo.getRandomFeed(
                                limit = limit,
                                excludeHostIds = exclude,
                                onlyActive = true
                            ).map { it.toUi() }
                        }
                    }
                } ?: throw IOException("network timeout")

                _state.value = _state.value.copy(loading = false, items = uiList, error = null)

                // Si hay datos, ocultar banner y detener reintentos (si los hubiera)
                if (uiList.isNotEmpty()) {
                    _bannerMessage.value = null
                    stopAutoRetry()
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
                    val uiList: List<Experience> = withTimeoutOrNull(5_000L) {
                        when (params.mode) {
                            Mode.FILTERED -> {
                                repo.getFilteredFeed(
                                    limit = params.limit,
                                    excludeHostEmails = params.exclude,
                                    department = params.department,
                                    startAtMs = params.startAtMs,
                                    endAtMs = params.endAtMs,
                                    onlyActive = params.onlyActive
                                ).map { it.toUi() }
                            }
                            Mode.RANDOM -> {
                                repo.getRandomFeed(
                                    limit = params.limit,
                                    excludeHostIds = params.exclude,
                                    onlyActive = params.onlyActive
                                ).map { it.toUi() }
                            }
                        }
                    } ?: emptyList()
                    uiList
                }.onSuccess { uiList ->
                    if (uiList.isNotEmpty()) {
                        _state.value = _state.value.copy(loading = false, items = uiList, error = null)
                        _bannerMessage.value = null
                        stopAutoRetry()
                    }
                }.onFailure {
                    // seguimos intentando; no cambiamos el banner aquí
                }
            }
        }
    }

    private fun stopAutoRetry() {
        autoRetryJob?.cancel()
        autoRetryJob = null
    }

    /* ===================== Heurística local para error de red ===================== */
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
                val uiList: List<Experience> = withTimeoutOrNull(5_000L) {
                    when (params.mode) {
                        // Usa EXACTAMENTE los mismos métodos y parámetros que ya usas
                        Mode.FILTERED -> {
                            repo.getFilteredFeed(
                                limit = params.limit,
                                excludeHostEmails = params.exclude,
                                department = params.department,
                                startAtMs = params.startAtMs,
                                endAtMs = params.endAtMs,
                                onlyActive = params.onlyActive
                            ).map { it.toUi() }
                        }
                        Mode.RANDOM -> {
                            repo.getRandomFeed(
                                limit = params.limit,
                                excludeHostIds = params.exclude,
                                onlyActive = params.onlyActive
                            ).map { it.toUi() }
                        }
                    }
                } ?: emptyList()
                uiList
            }.onSuccess { uiList ->
                if (uiList.isNotEmpty()) {
                    _state.value = _state.value.copy(loading = false, items = uiList, error = null)
                    _bannerMessage.value = null
                    stopAutoRetry()
                }
            }
        }
    }
}

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
