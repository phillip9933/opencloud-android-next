package eu.opencloud.android.next.feature.transfers

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.TransferDirection
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.sync.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun TransfersRoute(
    accountId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = viewModel(key = "transfers-$accountId"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    TransfersScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onResolveConflict = viewModel::resolveConflict,
        onRetryAll = viewModel::retryAll,
        onClearAll = viewModel::clearAll,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

class TransfersViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val manager = TransferManager(application, store)
    private val mutableState = MutableStateFlow(TransfersUiState())
    val state: StateFlow<TransfersUiState> = mutableState.asStateFlow()
    private var loadedAccountId: String? = null

    fun load(accountId: String) {
        if (loadedAccountId == accountId) return
        loadedAccountId = accountId
        viewModelScope.launch(Dispatchers.IO) {
            store.observeTransfers(accountId).collectLatest { transfers ->
                mutableState.value = categorizeTransfers(transfers)
            }
        }
    }

    fun retry(transfer: TransferEntity) = runAction { manager.retry(transfer) }

    fun cancel(transfer: TransferEntity) = runAction { manager.cancel(transfer) }

    fun resolveConflict(
        transfer: TransferEntity,
        decision: TransferConflictDecision,
    ) = runAction {
        when (decision) {
            TransferConflictDecision.REPLACE -> manager.retryConflict(transfer, overwrite = true)
            TransferConflictDecision.KEEP_BOTH -> manager.retryConflict(transfer, overwrite = false, keepBoth = true)
            TransferConflictDecision.CANCEL -> manager.cancelConflict(transfer)
        }
    }

    fun clearHistory() {
        val accountId = loadedAccountId ?: return
        runAction { manager.clearHistory(accountId) }
    }

    fun retryAll() {
        val failed = retryableTransfers(state.value.failed)
        if (failed.isEmpty()) return
        runAction {
            val failures = failed.mapNotNull { transfer -> runCatching { manager.retry(transfer) }.exceptionOrNull() }
            check(failures.isEmpty()) {
                "${failures.size} of ${failed.size} failed transfers could not be retried."
            }
        }
    }

    fun clearAll() {
        val accountId = loadedAccountId ?: return
        val transfers = state.value.active + state.value.failed + state.value.history
        runAction { manager.clearAll(accountId, transfers) }
    }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onFailure {
                    mutableState.value =
                        mutableState.value.copy(error = it.message ?: "Transfer action failed.")
                }
        }
    }
}

data class TransfersUiState(
    val active: List<TransferEntity> = emptyList(),
    val failed: List<TransferEntity> = emptyList(),
    val history: List<TransferEntity> = emptyList(),
    val error: String? = null,
)

enum class TransferConflictDecision { REPLACE, KEEP_BOTH, CANCEL }

internal fun categorizeTransfers(transfers: List<TransferEntity>): TransfersUiState =
    TransfersUiState(
        active = transfers.filter { it.state in ACTIVE_STATES },
        failed = transfers.filter { it.state in FAILED_STATES },
        history = transfers.filter { it.state in HISTORY_STATES },
    )

internal fun retryableTransfers(transfers: List<TransferEntity>): List<TransferEntity> =
    transfers.filter { it.state == TransferState.FAILED.name }

private val ACTIVE_STATES = setOf(TransferState.QUEUED.name, TransferState.RUNNING.name, TransferState.RETRY.name)
private val FAILED_STATES = setOf(TransferState.FAILED.name, TransferState.CONFLICT.name)
private val HISTORY_STATES = setOf(TransferState.SUCCEEDED.name, TransferState.CANCELLED.name)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")
fun TransfersScreen(
    state: TransfersUiState,
    onNavigateBack: () -> Unit,
    onRetry: (TransferEntity) -> Unit,
    onCancel: (TransferEntity) -> Unit,
    onResolveConflict: (TransferEntity, TransferConflictDecision) -> Unit,
    onRetryAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val hasFailedTransfers = state.failed.any { it.state == TransferState.FAILED.name }
    val hasTransfers = state.active.isNotEmpty() || state.failed.isNotEmpty() || state.history.isNotEmpty()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Transfers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasTransfers) {
                        Box {
                            IconButton(onClick = { showActions = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Transfer actions")
                            }
                            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                DropdownMenuItem(
                                    text = { Text("Retry all failed") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    enabled = hasFailedTransfers,
                                    onClick = {
                                        showActions = false
                                        onRetryAll()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear all") },
                                    leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null) },
                                    onClick = {
                                        showActions = false
                                        confirmClearAll = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.active.isEmpty() && state.failed.isEmpty() && state.history.isEmpty()) {
            TransferEmptyState(padding)
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                transferSection("Active & queued", state.active) { TransferRow(it, onCancel = { onCancel(it) }) }
                transferSection("Needs attention", state.failed) { transfer ->
                    TransferRow(
                        transfer = transfer,
                        onRetry = if (transfer.state == TransferState.FAILED.name) ({ onRetry(transfer) }) else null,
                        onConflict =
                            if (transfer.state == TransferState.CONFLICT.name) {
                                { decision -> onResolveConflict(transfer, decision) }
                            } else {
                                null
                            },
                    )
                }
                transferSection("History", state.history) { TransferRow(it) }
            }
        }
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Transfer action") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all transfers?") },
            text = { Text("Active work will be cancelled and the complete transfer list will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        onClearAll()
                    },
                ) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.transferSection(
    title: String,
    transfers: List<TransferEntity>,
    row: @Composable (TransferEntity) -> Unit,
) {
    if (transfers.isEmpty()) return
    item {
        Text(
            title,
            modifier = Modifier.padding(OpenCloudDimensions.SpacingMd),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    items(transfers, key = TransferEntity::id) { transfer -> row(transfer) }
}

@Composable
private fun TransferEmptyState(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Text("No transfers", style = MaterialTheme.typography.titleLarge)
            Text("Uploads and downloads will appear here.")
        }
    }
}

@Composable
private fun TransferRow(
    transfer: TransferEntity,
    onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onConflict: ((TransferConflictDecision) -> Unit)? = null,
) {
    val progress =
        if (transfer.bytesTotal > 0) {
            (transfer.bytesTransferred.toFloat() / transfer.bytesTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
    ListItem(
        headlineContent = { Text(transfer.displayName) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs)) {
                Text(transferStatus(transfer))
                if (transfer.state in ACTIVE_STATES) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
                transfer.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onConflict != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs)) {
                        TextButton(onClick = { onConflict(TransferConflictDecision.REPLACE) }) { Text("Replace") }
                        TextButton(onClick = { onConflict(TransferConflictDecision.KEEP_BOTH) }) { Text("Keep both") }
                        TextButton(onClick = { onConflict(TransferConflictDecision.CANCEL) }) { Text("Cancel") }
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                if (transfer.direction ==
                    TransferDirection.UPLOAD.name
                ) {
                    Icons.Default.CloudUpload
                } else {
                    Icons.Default.CloudDownload
                },
                contentDescription = null,
            )
        },
        trailingContent = {
            when {
                onRetry != null -> IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "Retry") }
                onCancel != null -> IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Cancel transfer") }
                transfer.state == TransferState.FAILED.name -> Icon(Icons.Default.Error, contentDescription = null)
            }
        },
    )
}

private fun transferStatus(transfer: TransferEntity): String =
    transfer.state.lowercase().replaceFirstChar(Char::uppercase) +
        if (transfer.bytesTotal > 0) " • ${transfer.bytesTransferred} / ${transfer.bytesTotal} bytes" else ""
