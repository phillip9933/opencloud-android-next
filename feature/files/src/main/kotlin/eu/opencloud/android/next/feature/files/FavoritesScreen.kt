package eu.opencloud.android.next.feature.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.sync.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FavoritesUiState(
    val resources: List<ResourceEntity> = emptyList(),
    val error: String? = null,
)

class FavoritesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val transfers = TransferManager(application, store)
    private val mutableState = MutableStateFlow(FavoritesUiState())
    val state = mutableState.asStateFlow()
    private var accountId: String? = null

    fun load(accountId: String) {
        if (this.accountId == accountId) return
        this.accountId = accountId
        viewModelScope.launch(Dispatchers.IO) {
            store.observeFavorites(accountId).collectLatest {
                mutableState.value =
                    mutableState.value.copy(resources = it)
            }
        }
    }

    fun remove(resource: ResourceEntity) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.setFavorite(resource, false) } }
                .onFailure {
                    mutableState.value =
                        mutableState.value.copy(error = it.message ?: "Favorite could not be removed.")
                }
        }
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }
}
