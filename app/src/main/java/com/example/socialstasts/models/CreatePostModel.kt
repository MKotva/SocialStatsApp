package com.example.socialstasts.createpost

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.socialstasts.helpers.PickedMedia
import com.example.socialstasts.helpers.resolveTargetAccounts
import com.example.socialstasts.persistance.AccountEntity
import com.example.socialstasts.persistance.AppDb
import com.example.socialstasts.persistance.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val ACCOUNT_ALL = "__ALL__"

data class CreatePostUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = true,
    val selectedAccKeys: Set<String> = setOf(ACCOUNT_ALL),
    val title: String = "",
    val description: String = "",
    val pickedMedia: PickedMedia? = null,
    val postCreated: Boolean = false
)

class CreatePostViewModel(
    private val repo: StatsRepository,
    savedState: SavedStateHandle,
    accName: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CreatePostUiState(
            selectedAccKeys = (savedState.get<ArrayList<String>>("selectedAccKeys")?.toSet())
                ?: accName?.let { setOf(it) }
                ?: setOf(ACCOUNT_ALL),
            title = savedState["title"] ?: "",
            description = savedState["description"] ?: "",
            pickedMedia = buildPickedMedia(
                mediaType = savedState["pickedMediaType"],
                mediaUri = savedState["pickedMediaUri"],
                displayName = savedState["pickedMediaDisplayName"]
            ),
            postCreated = false
        )
    )
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()
    private val state = savedState

    init {
        viewModelScope.launch {
            val accounts = repo.getAllAccounts()
            _uiState.value = _uiState.value.copy(
                accounts = accounts,
                isLoading = false,
                selectedAccKeys = when {
                    accName != null -> setOf(accName)
                    _uiState.value.selectedAccKeys.isNotEmpty() -> _uiState.value.selectedAccKeys
                    accounts.isNotEmpty() -> setOf(ACCOUNT_ALL)
                    else -> _uiState.value.selectedAccKeys
                }
            )
        }
    }

    fun onSelectedAccountsChanged(value: Set<String>) {
        state["selectedAccKeys"] = ArrayList(value.toList())
        _uiState.value = _uiState.value.copy(selectedAccKeys = value)
    }

    fun onTitleChange(value: String) {
        state["title"] = value
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun onDescriptionChange(value: String) {
        state["description"] = value
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun onPickedMedia(value: PickedMedia) {
        state["pickedMediaType"] = value.mediaType
        state["pickedMediaUri"] = value.mediaUri
        state["pickedMediaDisplayName"] = value.displayName
        _uiState.value = _uiState.value.copy(pickedMedia = value)
    }

    fun onPostCreatedConsumed() {
        _uiState.value = _uiState.value.copy(postCreated = false)
    }

    fun createPost() {
        val current = _uiState.value
        val picked = current.pickedMedia ?: return
        val targets = resolveTargetAccounts(current.accounts, current.selectedAccKeys)
        if (current.title.isBlank() || targets.isEmpty()) return

        viewModelScope.launch {
            repo.createPostForTargets(
                targets = targets,
                title = current.title.trim(),
                description = current.description.trim(),
                picked = picked
            )
            _uiState.value = _uiState.value.copy(postCreated = true)
        }
    }

    companion object {
        private fun buildPickedMedia(mediaType: String?, mediaUri: String?, displayName: String?): PickedMedia? {
            if (mediaType == null || mediaUri == null || displayName == null) return null
            return PickedMedia(
                mediaType = mediaType,
                mediaUri = mediaUri,
                displayName = displayName
            )
        }
    }
}

object CreatePostViewModelFactory {
    fun provideFactory(selectedAccName: String?) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val context = checkNotNull(
                extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            ).applicationContext

            val db = AppDb.get(context)
            val repo = StatsRepository(db, db.statsDao())
            val savedState = extras.createSavedStateHandle()

            return CreatePostViewModel(
                repo = repo,
                savedState = savedState,
                accName = selectedAccName
            ) as T
        }
    }
}