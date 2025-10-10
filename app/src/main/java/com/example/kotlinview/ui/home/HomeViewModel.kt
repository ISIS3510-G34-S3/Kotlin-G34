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

data class FeedUiState(
    val loading: Boolean = false,
    val items: List<Experience> = emptyList(), // UI model de este paquete
    val error: Throwable? = null
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state

    fun loadFeed(
        limit: Int = 20,
        department: String? = null,
        startAtMs: Long? = null,
        endAtMs: Long? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val emailLower = SessionManager.currentUser.value?.email?.trim()?.lowercase().orEmpty()
                val exclude = if (emailLower.isNotBlank()) setOf(emailLower) else emptySet()

                val repo = ServiceLocator.provideExperiencesRepository()

                if (department != null || (startAtMs != null && endAtMs != null)) {
                    repo.getFilteredFeed(
                        limit = limit,
                        excludeHostEmails = exclude,
                        department = department,
                        startAtMs = startAtMs,
                        endAtMs = endAtMs,
                        onlyActive = true
                    ).map { it.toUi() }
                } else {
                    repo.getRandomFeed(
                        limit = limit,
                        excludeHostIds = exclude,
                        onlyActive = true
                    ).map { it.toUi() }
                }
            }.onSuccess { uiList ->
                _state.value = _state.value.copy(loading = false, items = uiList, error = null)
            }.onFailure { e ->
                // Log para depurar si vuelve a fallar
                android.util.Log.e("Feed", "loadFeed error", e)
                _state.value = _state.value.copy(loading = false, error = e)
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
        hostName     = this.hostName
    )


