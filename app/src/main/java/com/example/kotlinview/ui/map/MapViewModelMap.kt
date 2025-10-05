package com.example.kotlinview.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kotlinview.data.experiences.ExperiencesRepository
import com.example.kotlinview.data.map.ExperienceDtoMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MapUiStateMap(
    val isLoading: Boolean = false,
    val error: String? = null,
    val items: List<ExperienceDtoMap> = emptyList()
)

class MapViewModelMap(
    private val repo: ExperiencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiStateMap())
    val state: StateFlow<MapUiStateMap> = _state

    fun fetchNearest(lat: Double, lng: Double, topK: Int = 20) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val list = repo.getNearest(lat, lng, topK)
                _state.value = _state.value.copy(isLoading = false, items = list)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

class MapVmFactoryMap(
    private val repo: ExperiencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModelMap::class.java))
        return MapViewModelMap(repo) as T
    }
}
