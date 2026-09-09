package eu.opencloud.android.next.feature.files

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.model.ResourceKind
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

@Composable
fun FavoritesContent(
    state: FavoritesUiState,
    contentPadding: PaddingValues,
    onRemove: (ResourceEntity) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.resources.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text("No favorites yet", style = MaterialTheme.typography.titleMedium)
                    Text("Favorite files remain listed here while offline.")
                }
            }
        } else {
            LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                items(state.resources, key = { "${it.spaceId}:${it.remoteId}" }) { resource ->
                    ListItem(
                        headlineContent = { Text(resource.name) },
                        supportingContent = { Text(resource.path) },
                        leadingContent = {
                            Icon(
                                if (resource.kind ==
                                    ResourceKind.FOLDER
                                ) {
                                    Icons.Default.Folder
                                } else {
                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                            )
                        },
                        trailingContent = { TextButton(onClick = { onRemove(resource) }) { Text("Remove") } },
                    )
                }
            }
        }
        state.error?.let { message ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("Favorites") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
            )
        }
    }
}
