package eu.opencloud.android.next.feature.files

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.network.RemoteTrashResource
import eu.opencloud.android.next.core.sync.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeletedFilesUiState(
    val supported: Boolean = true,
    val loading: Boolean = false,
    val resources: List<RemoteTrashResource> = emptyList(),
    val error: String? = null,
)

class DeletedFilesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val manager = TrashManager(application)
    private val mutableState = MutableStateFlow(DeletedFilesUiState())
    val state = mutableState.asStateFlow()
    private var accountId: String? = null

    fun load(accountId: String) {
        this.accountId = accountId
        refresh()
    }

    fun refresh() {
        val accountId = accountId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    manager.load(accountId)
                }
            }.onSuccess { resources ->
                mutableState.value = DeletedFilesUiState(supported = resources != null, resources = resources.orEmpty())
            }.onFailure { mutableState.value = mutableState.value.copy(loading = false, error = it.message) }
        }
    }

    fun restore(resource: RemoteTrashResource) = mutate(resource) { account, item -> manager.restore(account, item) }

    fun delete(resource: RemoteTrashResource) =
        mutate(resource) { account, item -> manager.permanentlyDelete(account, item) }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun mutate(
        resource: RemoteTrashResource,
        action: suspend (String, RemoteTrashResource) -> Unit,
    ) {
        val account = accountId ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action(account, resource) } }
                .onSuccess { refresh() }
                .onFailure { mutableState.value = mutableState.value.copy(error = it.message) }
        }
    }
}

@Composable
fun DeletedFilesRoute(
    accountId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeletedFilesViewModel = viewModel(key = "deleted-files-$accountId"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    DeletedFilesScreen(
        state,
        onNavigateBack,
        viewModel::refresh,
        viewModel::restore,
        viewModel::delete,
        viewModel::dismissError,
        modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun DeletedFilesScreen(
    state: DeletedFilesUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (RemoteTrashResource) -> Unit,
    onDelete: (RemoteTrashResource) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf<RemoteTrashResource?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = {
                Text("Deleted files")
            }, navigationIcon = {
                IconButton(
                    onClick = onNavigateBack,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
    ) { padding ->
        when {
            state.loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            !state.supported -> EmptyDeletedState("Deleted files are not supported by this server.", padding, null)
            state.resources.isEmpty() -> EmptyDeletedState("The trash bin is empty.", padding, onRefresh)
            else ->
                LazyColumn(contentPadding = padding) {
                    items(state.resources, key = { "${it.spaceId}:${it.id}" }) { resource ->
                        ListItem(
                            headlineContent = { Text(resource.name) },
                            supportingContent = { Text(resource.originalPath) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(
                                        onClick = { onRestore(resource) },
                                    ) { Icon(Icons.Default.Restore, "Restore ${resource.name}") }
                                    IconButton(onClick = {
                                        confirmDelete = resource
                                    }) { Icon(Icons.Default.DeleteForever, "Permanently delete ${resource.name}") }
                                }
                            },
                        )
                    }
                }
        }
    }
    confirmDelete?.let { resource ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Permanently delete?") },
            text = { Text("${resource.name} cannot be restored after this action.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(resource)
                }) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    state.error?.let {
        AlertDialog(onDismissRequest = onDismissError, title = {
            Text("Deleted files")
        }, text = { Text(it) }, confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } })
    }
}

@Composable
private fun EmptyDeletedState(
    message: String,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onRefresh: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Text(message)
            onRefresh?.let { TextButton(onClick = it) { Text("Refresh") } }
        }
    }
}
