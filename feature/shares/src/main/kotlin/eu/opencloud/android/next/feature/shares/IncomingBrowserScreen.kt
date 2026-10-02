package eu.opencloud.android.next.feature.shares

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.documentsprovider.sharedFileViewIntent
import eu.opencloud.android.next.core.model.fileMimeType
import eu.opencloud.android.next.core.network.safeMessage

@Composable
internal fun IncomingBrowserRoute(account: String) {
    val context = LocalContext.current
    val owner = rememberCoroutineScope()
    var savedTrail by rememberSaveable(account) { mutableStateOf(emptyList<String>()) }
    val controller =
        remember(account) {
            val backend = AndroidIncomingBrowserBackend(context)
            IncomingBrowserController(
                owner,
                backend,
                errorMessage = { it.safeMessage(context) },
                onTrailChanged = { savedTrail = IncomingBrowserTrail.save(it) },
                performCopyAction = backend::changeCopy,
            )
        }
    val state by controller.state.collectAsState()
    var openError by remember(account) { mutableStateOf<String?>(null) }
    val noAppError = stringResource(R.string.incoming_open_no_app)
    val accessError = stringResource(R.string.incoming_open_access_unavailable)
    val upload = incomingUploadPicker(state, controller) { openError = it }
    LaunchedEffect(controller) { controller.load(account, IncomingBrowserTrail.restore(account, savedTrail)) }
    DisposableEffect(controller) { onDispose { controller.clear() } }
    BackHandler(state.trail.isNotEmpty()) { controller.back() }
    IncomingBrowserScreen(state, controller::back, {
        openError = null
        controller.refresh()
    }, { item ->
        openError = null
        when (item) {
            is IncomingBrowserItem.Folder -> controller.open(item)
            is IncomingBrowserItem.File -> {
                try {
                    context.startActivity(
                        sharedFileViewIntent(context, item.request, fileMimeType(item.name, item.mimeType)),
                    )
                } catch (_: ActivityNotFoundException) {
                    openError = noAppError
                } catch (_: SecurityException) {
                    openError = accessError
                }
            }
        }
    }, openError, controller::changeCopy, upload)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // Independent navigation, opening and local-copy actions.
internal fun IncomingBrowserScreen(
    state: IncomingBrowserState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (IncomingBrowserItem) -> Unit,
    openError: String?,
    onCopyAction: (IncomingBrowserItem.File, SharedCopyAction) -> Unit,
    onUpload: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row {
            if (state.trail.isNotEmpty()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.incoming_parent_folder))
                }
            }
            Text(
                state.trail.lastOrNull()?.name ?: stringResource(R.string.incoming_shared_folders),
                Modifier.weight(1f).padding(OpenCloudDimensions.SpacingMd),
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, stringResource(R.string.incoming_refresh_folder))
            }
            if (state.uploadDestination != null) {
                TextButton(onClick = onUpload) { Text(stringResource(R.string.incoming_upload)) }
            }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    IncomingBrowserNotice(state, openError, onRefresh)
                }
                items(state.items) { item ->
                    IncomingBrowserRow(item, onOpen, onCopyAction)
                }
            }
        }
    }
}

@Composable
private fun incomingUploadPicker(
    state: IncomingBrowserState,
    controller: IncomingBrowserController,
    onError: (String?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val sourceAccessError = stringResource(R.string.incoming_upload_source_unavailable)
    var target by rememberSaveable(state.account) { mutableStateOf(emptyList<String>()) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val destination = IncomingBrowserTrail.restore(state.account.orEmpty(), target).lastOrNull()?.request
            target = emptyList()
            if (uri != null && destination != null && destination == state.uploadDestination) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    onError(null)
                    controller.upload(destination, uri.toString())
                } catch (_: SecurityException) {
                    onError(sourceAccessError)
                }
            }
        }
    return {
        target = IncomingBrowserTrail.save(state.trail)
        picker.launch(arrayOf("*/*"))
    }
}

@Composable
private fun IncomingBrowserNotice(
    state: IncomingBrowserState,
    openError: String?,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(OpenCloudDimensions.SpacingMd)) {
        if (state.unavailable > 0) {
            Text(
                pluralStringResource(
                    R.plurals.incoming_unavailable_folders,
                    state.unavailable,
                    state.unavailable,
                ),
            )
        }
        val error = openError ?: state.error
        if (error != null) {
            Text(error)
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.incoming_try_again)) }
        } else if (!state.loading && state.items.isEmpty()) {
            Text(
                stringResource(
                    if (state.trail.isEmpty()) R.string.incoming_no_shared_folders else R.string.incoming_empty_folder,
                ),
            )
        }
    }
}

@Composable
private fun IncomingBrowserRow(
    item: IncomingBrowserItem,
    onOpen: (IncomingBrowserItem) -> Unit,
    onCopyAction: (IncomingBrowserItem.File, SharedCopyAction) -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.name) },
        leadingContent = {
            Icon(
                if (item is IncomingBrowserItem.Folder) {
                    Icons.Default.Folder
                } else {
                    Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
            )
        },
        supportingContent = {
            if (item is IncomingBrowserItem.File) {
                Text(
                    when (item.localCopy?.offlinePinned) {
                        true -> stringResource(R.string.incoming_copy_kept_online_required)
                        false -> stringResource(R.string.incoming_copy_temporary)
                        null -> stringResource(R.string.incoming_copy_cloud_only)
                    },
                )
            }
        },
        trailingContent = {
            if (item is IncomingBrowserItem.File) IncomingCopyMenu(item, onCopyAction)
        },
        modifier = Modifier.clickable { onOpen(item) },
    )
}
