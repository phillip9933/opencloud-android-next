package eu.opencloud.android.next.feature.files

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.network.RemoteTrashResource
import eu.opencloud.android.next.core.network.safeMessage
import eu.opencloud.android.next.core.network.toOpenCloudError
import eu.opencloud.android.next.core.sync.TrashManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeletedFilesUiState(
    val supported: Boolean = true,
    val loading: Boolean = false,
    val mutating: Boolean = false,
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
        if (state.value.loading || state.value.mutating) return
        val accountId = accountId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    manager.load(accountId)
                }
            }.onSuccess { resources ->
                mutableState.value = DeletedFilesUiState(supported = resources != null, resources = resources.orEmpty())
            }.onFailure {
                if (it is CancellationException) throw it
                mutableState.value =
                    mutableState.value.copy(
                        loading = false,
                        error = it.toOpenCloudError().safeMessage(getApplication()),
                    )
            }
        }
    }

    fun restore(resource: RemoteTrashResource) = restoreMany(listOf(resource))

    fun delete(resource: RemoteTrashResource) = deleteMany(listOf(resource))

    fun restoreMany(resources: List<RemoteTrashResource>) =
        mutate(resources) { account, item -> manager.restore(account, item) }

    fun deleteMany(resources: List<RemoteTrashResource>) =
        mutate(resources) { account, item -> manager.permanentlyDelete(account, item) }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    @Suppress("TooGenericExceptionCaught") // Batch boundary retains failures; cancellation must escape.
    private fun mutate(
        resources: List<RemoteTrashResource>,
        action: suspend (String, RemoteTrashResource) -> Unit,
    ) {
        val account = accountId ?: return
        if (state.value.loading || state.value.mutating) return
        mutableState.value = state.value.copy(mutating = true, error = null)
        viewModelScope.launch {
            var failed = 0
            var reason: String? = null
            try {
                resources.distinctBy { it.selectionKey }.forEach { item ->
                    try {
                        withContext(Dispatchers.IO) { action(account, item) }
                        mutableState.value =
                            state.value.copy(
                                resources =
                                    state.value.resources.filterNot {
                                        it.selectionKey ==
                                            item.selectionKey
                                    },
                            )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failed++
                        reason = error.toOpenCloudError().safeMessage(getApplication())
                    }
                }
                if (failed >
                    0
                ) {
                    mutableState.value =
                        state.value.copy(
                            error =
                                ContextCompat
                                    .getContextForLanguage(
                                        getApplication<Application>(),
                                    ).resources
                                    .getQuantityString(
                                        R.plurals.deleted_batch_failure,
                                        failed,
                                        failed,
                                        reason.orEmpty(),
                                    ),
                        )
                }
            } finally {
                mutableState.value = state.value.copy(mutating = false)
            }
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
        viewModel::restoreMany,
        viewModel::deleteMany,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
fun DeletedFilesScreen(
    state: DeletedFilesUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (RemoteTrashResource) -> Unit,
    onDelete: (RemoteTrashResource) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onRestoreMany: (List<RemoteTrashResource>) -> Unit = { it.forEach(onRestore) },
    onDeleteMany: (List<RemoteTrashResource>) -> Unit = { it.forEach(onDelete) },
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf<List<RemoteTrashResource>?>(null) }
    val busy = state.loading || state.mutating
    val selection = state.resources.filter { it.selectionKey in selected }
    LaunchedEffect(state.resources) { selected = selected.intersect(state.resources.map { it.selectionKey }.toSet()) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selection.isEmpty()) {
                            stringResource(R.string.deleted_files_title)
                        } else {
                            pluralStringResource(R.plurals.deleted_selection_count, selection.size, selection.size)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selected.isEmpty()) onNavigateBack() else selected = emptySet() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.deleted_back))
                    }
                },
            )
        },
    ) { outerPadding ->
        Column(Modifier.fillMaxSize().padding(outerPadding)) {
            if (state.supported) {
                StorageSummary(
                    title = stringResource(R.string.deleted_recycle_bin_title),
                    explanation = stringResource(R.string.deleted_recycle_bin_explanation),
                    bytes = state.resources.sumOf { it.sizeBytes ?: 0 },
                    incomplete = state.resources.any { it.sizeBytes == null },
                    action = stringResource(R.string.deleted_empty_recycle_bin),
                    enabled = state.resources.isNotEmpty() && !busy,
                    onAction = { confirmDelete = state.resources.toList() },
                )
                TrashSelectionActions(
                    state.resources,
                    selection,
                    busy,
                    onSelectionChange = { selected = it },
                    onRestore = { onRestoreMany(selection) },
                    onDelete = { confirmDelete = selection },
                )
            }
            if (state.mutating) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            TrashList(
                state,
                selected,
                busy,
                onRefresh,
                onRestore,
                onDelete = { confirmDelete = listOf(it) },
                onToggle = { resource ->
                    selected =
                        if (resource.selectionKey in
                            selected
                        ) {
                            selected - resource.selectionKey
                        } else {
                            selected + resource.selectionKey
                        }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
    confirmDelete?.let { resources ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = {
                Text(pluralStringResource(R.plurals.deleted_confirm_title, resources.size, resources.size))
            },
            text = {
                Text(pluralStringResource(R.plurals.deleted_confirm_message, resources.size, resources.size))
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmDelete = null
                    onDeleteMany(resources)
                }) { Text(stringResource(R.string.deleted_delete_permanently)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.deleted_cancel)) }
            },
        )
    }
    state.error?.let {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.deleted_files_title)) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.deleted_ok)) } },
        )
    }
}

private val RemoteTrashResource.selectionKey: String get() = "$spaceId\u0000$id"

@Composable
@Suppress("LongParameterList")
private fun TrashRow(
    resource: RemoteTrashResource,
    selected: Boolean,
    selectionMode: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(resource.name) },
        supportingContent = { Text(resource.originalPath) },
        modifier = Modifier.combinedClickable(enabled = !busy, onClick = onToggle, onLongClick = onToggle),
        leadingContent = { Checkbox(selected, { onToggle() }, enabled = !busy) },
        trailingContent = {
            if (!selectionMode) {
                Row {
                    IconButton(
                        enabled = !busy,
                        onClick = onRestore,
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            stringResource(R.string.deleted_restore_item, resource.name),
                        )
                    }
                    IconButton(enabled = !busy, onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteForever,
                            stringResource(R.string.deleted_permanently_delete_item, resource.name),
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun TrashList(
    state: DeletedFilesUiState,
    selected: Set<String>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onRestore: (RemoteTrashResource) -> Unit,
    onDelete: (RemoteTrashResource) -> Unit,
    onToggle: (RemoteTrashResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { if (!busy) onRefresh() }, modifier = modifier) {
        if (state.resources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        state.loading -> stringResource(R.string.deleted_loading)
                        state.supported -> stringResource(R.string.deleted_empty)
                        else -> stringResource(R.string.deleted_unsupported)
                    },
                )
            }
        } else {
            LazyColumn {
                items(state.resources, key = { it.selectionKey }) { resource ->
                    TrashRow(
                        resource,
                        resource.selectionKey in selected,
                        selected.isNotEmpty(),
                        busy,
                        onToggle = { onToggle(resource) },
                        onRestore = { onRestore(resource) },
                        onDelete = { onDelete(resource) },
                    )
                }
            }
        }
    }
}

private fun allTrashSelected(
    selection: List<RemoteTrashResource>,
    resources: List<RemoteTrashResource>,
) = selection.isNotEmpty() && selection.size == resources.size

@Composable
@Suppress("LongParameterList")
private fun TrashSelectionActions(
    resources: List<RemoteTrashResource>,
    selection: List<RemoteTrashResource>,
    busy: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val allSelected = allTrashSelected(selection, resources)
    Row {
        TextButton(enabled = !busy && resources.isNotEmpty(), onClick = {
            onSelectionChange(if (allSelected) emptySet() else resources.map { it.selectionKey }.toSet())
        }) {
            Text(
                stringResource(if (allSelected) R.string.deleted_clear_selection else R.string.deleted_select_all),
            )
        }
        if (selection.isNotEmpty()) {
            IconButton(enabled = !busy, onClick = onRestore) {
                Icon(Icons.Default.Restore, stringResource(R.string.deleted_restore_selected))
            }
            IconButton(
                enabled = !busy,
                onClick = onDelete,
            ) { Icon(Icons.Default.DeleteForever, stringResource(R.string.deleted_permanently_delete_selected)) }
        }
    }
}
