package com.example.kotlinview.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.kotlinview.core.SessionManager
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.model.Experience
import androidx.lifecycle.viewModelScope
import com.example.kotlinview.data.map.ExperienceDtoMap
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<ExperienceDtoMap>>(emptyList())
    val items: StateFlow<List<ExperienceDtoMap>> = _items

    fun loadRandomExperiences(limit: Int = 20) {
        viewModelScope.launch {
            val currentEmail = SessionManager.currentUser.value?.email.orEmpty()
            val repo = ServiceLocator.provideExperiencesRepository()
            val all = repo.getExperiences(limit = 50)

            val filtered = if (currentEmail.isNotBlank())
                all.filter { (it.hostId ?: "") != currentEmail }   // hostId guarda el email del host
            else
                all

            _items.value = filtered.shuffled().take(limit)
        }
    }
}