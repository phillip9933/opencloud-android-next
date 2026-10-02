package eu.opencloud.android.next.feature.spaces

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.sync.SpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

data class SpacesUiState(
    val spaces: List<SpaceEntity> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class SpacesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = SpaceRepository(FileBrowserStore(FileBrowserDatabase.create(application)))
    private val mutableState = MutableStateFlow(SpacesUiState())
    val state = mutableState.asStateFlow()
    private var accountId: String? = null

    fun refresh() {
        val account = accountId ?: return
        val workId =
            eu.opencloud.android.next.core.sync
                .TransferManager(getApplication())
                .refreshAccount(account)
        viewModelScope.launch {
            androidx.work.WorkManager.getInstance(getApplication()).getWorkInfoByIdFlow(workId).collectLatest { work ->
                mutableState.value =
                    mutableState.value.copy(loading = work?.state == androidx.work.WorkInfo.State.RUNNING)
            }
        }
    }

    fun load(accountId: String) {
        if (this.accountId == accountId) return
        this.accountId = accountId
        mutableState.value = SpacesUiState()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.observeProjectSpaces(accountId).collectLatest { spaces ->
                    mutableState.value = SpacesUiState(spaces = spaces, loading = false)
                }
            }.onFailure { error ->
                mutableState.value =
                    SpacesUiState(
                        loading = false,
                        error = error.message ?: getApplication<Application>().getString(R.string.spaces_load_failed),
                    )
            }
        }
    }
}

@Composable
fun SpacesRoute(
    accountId: String,
    onOpenSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpacesViewModel = viewModel(key = "spaces-$accountId"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    SpacesScreen(state = state, onOpenSpace = onOpenSpace, modifier = modifier, onRefresh = viewModel::refresh)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SpacesScreen(
    state: SpacesUiState,
    onOpenSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        when {
            state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null -> SpacesMessage(state.error)
            state.spaces.isEmpty() -> SpacesMessage(stringResource(R.string.spaces_none_available))
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(OpenCloudDimensions.SpacingMd),
                    verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
                ) {
                    items(state.spaces, key = { it.driveId }) { space ->
                        SpaceCard(space = space, onOpen = { onOpenSpace(space.driveId) })
                    }
                }
        }
    }
}

@Composable
private fun SpacesMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
        ) {
            Icon(Icons.Default.Apps, contentDescription = null)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpaceCard(
    space: SpaceEntity,
    onOpen: () -> Unit,
) {
    val quota = space.quotaSummary()
    val openSpaceDescription = stringResource(R.string.spaces_open_space, space.name)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = openSpaceDescription }
                .clickable(onClick = onOpen),
    ) {
        ListItem(
            headlineContent = { Text(space.name) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs)) {
                    space.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                    space.ownerName?.takeIf(String::isNotBlank)?.let {
                        Text(stringResource(R.string.spaces_owner, it))
                    }
                    quota?.let { summary ->
                        Text(
                            stringResource(R.string.spaces_quota_summary, summary.used, summary.remaining),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = { summary.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            leadingContent = { Icon(Icons.Default.Apps, contentDescription = null) },
        )
    }
}

private data class QuotaSummary(
    val used: String,
    val remaining: String,
    val progress: Float,
)

private fun SpaceEntity.quotaSummary(): QuotaSummary? {
    val used = quotaUsedBytes ?: -1
    val total = quotaBytes ?: quotaRemainingBytes?.let { used + it } ?: -1
    if (used < 0 || total <= 0) return null
    val remaining = quotaRemainingBytes ?: (total - used).coerceAtLeast(0)
    return QuotaSummary(
        used = used.toReadableSize(),
        remaining = remaining.toReadableSize(),
        progress = used.toFloat().div(total.toFloat()).coerceIn(0f, 1f),
    )
}

private fun Long.toReadableSize(): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) {
        "$this ${units[unit]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
