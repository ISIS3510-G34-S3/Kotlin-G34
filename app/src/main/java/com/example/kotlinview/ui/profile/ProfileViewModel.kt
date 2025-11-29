package com.example.kotlinview.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kotlinview.data.profile.LocalProfile
import com.example.kotlinview.data.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: LocalProfile? = null,
    val isEditing: Boolean = false,
    val draftName: String = "",
    val draftAbout: String = "",
    val draftLanguages: String = "",
    val showEmptyState: Boolean = false,
    val pendingSync: Boolean = false
)

class ProfileViewModel(
    private val repo: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    private var hasAttemptedRefresh = false

    init {
        // 1) Start collecting local state immediately
        viewModelScope.launch {
            repo.profileFlow.collectLatest { p ->
                val current = _state.value
                val pendingSync = p?.hasPendingSync == true

                // Only show empty-state after we tried refresh at least once
                val showEmpty = hasAttemptedRefresh && p == null

                _state.value = current.copy(
                    isLoading = (p == null && !hasAttemptedRefresh) || (p == null && current.isLoading && !hasAttemptedRefresh),
                    profile = p,
                    showEmptyState = showEmpty,
                    pendingSync = pendingSync
                )

                // Keep drafts aligned when not editing
                val st = _state.value
                if (p != null && !st.isEditing) {
                    _state.value = st.copy(
                        isLoading = false,
                        draftName = p.name,
                        draftAbout = p.about,
                        draftLanguages = p.languages.joinToString(", ")
                    )
                }
                if (p != null && st.isLoading) {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }

        // 2) Immediately attempt refresh once (prevents initial flicker)
        viewModelScope.launch {
            runCatching { repo.refreshFromRemote() }
            hasAttemptedRefresh = true
            val st = _state.value
            if (st.profile == null) {
                _state.value = st.copy(isLoading = false, showEmptyState = true)
            }
        }
    }

    fun refresh() {
        // Do not flip to empty-state while refreshing; just best-effort update.
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = _state.value.profile == null)
            runCatching { repo.refreshFromRemote() }
            hasAttemptedRefresh = true
            val st = _state.value
            if (st.profile == null) {
                _state.value = st.copy(isLoading = false, showEmptyState = true)
            } else {
                _state.value = st.copy(isLoading = false, showEmptyState = false)
            }
        }
    }

    fun startEdit() {
        val p = _state.value.profile ?: return
        _state.value = _state.value.copy(
            isEditing = true,
            draftName = p.name,
            draftAbout = p.about,
            draftLanguages = p.languages.joinToString(", ")
        )
    }

    fun cancelEdit() {
        val p = _state.value.profile
        _state.value = _state.value.copy(
            isEditing = false,
            draftName = p?.name.orEmpty(),
            draftAbout = p?.about.orEmpty(),
            draftLanguages = p?.languages?.joinToString(", ").orEmpty()
        )
    }

    fun setDraftName(v: String) { _state.value = _state.value.copy(draftName = v) }
    fun setDraftAbout(v: String) { _state.value = _state.value.copy(draftAbout = v) }
    fun setDraftLanguages(v: String) { _state.value = _state.value.copy(draftLanguages = v) }

    fun save() {
        val name = _state.value.draftName.trim()
        val about = _state.value.draftAbout.trim()
        val languages = _state.value.draftLanguages
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Exit edit mode immediately to avoid race with local emissions
        _state.value = _state.value.copy(isEditing = false)

        viewModelScope.launch {
            runCatching { repo.saveEdits(name, about, languages) }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching { repo.setPendingPhoto(uri) }
        }
    }
}

class ProfileVmFactory(
    private val repo: ProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
        return ProfileViewModel(repo) as T
    }
}
