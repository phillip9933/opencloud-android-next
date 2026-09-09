package eu.opencloud.android.next.feature.files

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudColor
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.model.ResourceKind
import kotlinx.coroutines.launch

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun FileBrowserRoute(
    accountId: String,
    releaseVersion: String,
    destinations: FileBrowserDestinations,
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = viewModel(key = "files-$accountId"),
    favoritesViewModel: FavoritesViewModel = viewModel(key = "favorites-$accountId"),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
    val uploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { selected ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        selected,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.upload(selected)
            }
        }
    viewModel.load(accountId)
    favoritesViewModel.load(accountId)
    FileBrowserScreen(
        accountId = accountId,
        releaseVersion = releaseVersion,
        state = state,
        favoritesState = favoritesState,
        modifier = modifier,
        onSelectSpace = viewModel::selectSpace,
        onOpen = viewModel::open,
        onNavigateUp = viewModel::navigateUp,
        onSetLayout = viewModel::setLayout,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onDownloadSelection = viewModel::downloadSelection,
        onDeleteSelection = viewModel::deleteSelected,
        onShowActions = viewModel::showActions,
        onDismissActions = viewModel::dismissActions,
        onCreateFolder = viewModel::createFolder,
        onCreateSpace = viewModel::createSpace,
        onRename = viewModel::rename,
        onMove = viewModel::move,
        onCopy = viewModel::copy,
        onDelete = viewModel::delete,
        onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
        onDownloadForOffline = viewModel::downloadForOffline,
        onToggleFavorite = viewModel::toggleFavorite,
        onResolveConflict = viewModel::resolveConflict,
        onClearMessage = viewModel::clearMessage,
        onGlobalAction = viewModel::showGlobalActionUnavailable,
        onSearchQueryChange = viewModel::setSearchQuery,
        onOpenTransfers = destinations.onOpenTransfers,
        onRemoveFavorite = favoritesViewModel::remove,
        onDismissFavoriteError = favoritesViewModel::dismissError,
        onOpenDeletedFiles = destinations.onOpenDeletedFiles,
        onOpenSettings = destinations.onOpenSettings,
        onOpenAccount = destinations.onOpenAccount,
    )
}

data class FileBrowserDestinations(
    val onOpenTransfers: () -> Unit,
    val onOpenDeletedFiles: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAccount: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList")
fun FileBrowserScreen(
    accountId: String,
    releaseVersion: String,
    state: FileBrowserUiState,
    favoritesState: FavoritesUiState = FavoritesUiState(),
    onSelectSpace: (String) -> Unit,
    onOpen: (ResourceEntity) -> Unit,
    onNavigateUp: () -> Unit,
    onSetLayout: (BrowserLayout) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onShowActions: (ResourceEntity?) -> Unit,
    onDismissActions: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateSpace: (String) -> Unit,
    onRename: (ResourceEntity, String) -> Unit,
    onMove: (ResourceEntity) -> Unit,
    onCopy: (ResourceEntity) -> Unit,
    onDelete: (ResourceEntity) -> Unit,
    onUpload: () -> Unit,
    onDownloadForOffline: (ResourceEntity) -> Unit,
    onToggleFavorite: (ResourceEntity) -> Unit,
    onResolveConflict: (TransferEntity, ConflictDecision) -> Unit,
    onClearMessage: () -> Unit,
    onGlobalAction: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenTransfers: () -> Unit,
    onRemoveFavorite: (ResourceEntity) -> Unit = {},
    onDismissFavoriteError: () -> Unit = {},
    onOpenDeletedFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    initialDestination: FileBrowserDestination = FileBrowserDestination.Personal,
) {
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var sortCriterion by remember { mutableStateOf(BrowserSortCriterion.Name) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedDestination by rememberSaveable { mutableStateOf(initialDestination) }
    val selectionMode = selectedDestination == FileBrowserDestination.Personal && state.selectedIds.isNotEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val visibleResources =
        remember(state.resources, state.searchQuery, state.searchResults, sortCriterion, sortAscending) {
            val source = if (state.searchQuery.isBlank()) state.resources else state.searchResults
            source
                .sortedWith(sortCriterion.comparator(sortAscending))
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !selectionMode,
        drawerContent = {
            BrowserNavigationDrawer(
                releaseVersion = releaseVersion,
                onTransfers = {
                    scope.launch { drawerState.close() }
                    onOpenTransfers()
                },
                onDeletedFiles = {
                    scope.launch { drawerState.close() }
                    onOpenDeletedFiles()
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (selectionMode) {
                    Column {
                        SelectionTopAppBar(
                            selectedCount = state.selectedIds.size,
                            onClearSelection = onClearSelection,
                            onDownloadSelection = onDownloadSelection,
                            onDeleteSelection = onDeleteSelection,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                BrowserSubHeader(
                                    canNavigateUp = state.folderTrail.isNotEmpty(),
                                    layout = state.layout,
                                    sortCriterion = sortCriterion,
                                    sortAscending = sortAscending,
                                    showSortMenu = showSortMenu,
                                    onNavigateUp = onNavigateUp,
                                    onShowSortMenu = { showSortMenu = true },
                                    onDismissSortMenu = { showSortMenu = false },
                                    onSortSelect = { criterion ->
                                        if (sortCriterion == criterion) {
                                            sortAscending = !sortAscending
                                        } else {
                                            sortCriterion = criterion
                                            sortAscending = true
                                        }
                                        showSortMenu = false
                                    },
                                    onToggleLayout = {
                                        onSetLayout(
                                            if (state.layout == BrowserLayout.TILES) {
                                                BrowserLayout.DEFAULT_TABLE
                                            } else {
                                                BrowserLayout.TILES
                                            },
                                        )
                                    },
                                )
                                TransferSummary(state.transfers)
                            }
                        }
                    }
                } else if (selectedDestination == FileBrowserDestination.Favorites) {
                    FavoritesTopAppBar(
                        accountId = accountId,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenAccount = onOpenAccount,
                    )
                } else {
                    Column {
                        BrowserTopAppBar(
                            accountId = accountId,
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenAccount = onOpenAccount,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                BrowserSubHeader(
                                    canNavigateUp = state.folderTrail.isNotEmpty(),
                                    layout = state.layout,
                                    sortCriterion = sortCriterion,
                                    sortAscending = sortAscending,
                                    showSortMenu = showSortMenu,
                                    onNavigateUp = onNavigateUp,
                                    onShowSortMenu = { showSortMenu = true },
                                    onDismissSortMenu = { showSortMenu = false },
                                    onSortSelect = { criterion ->
                                        if (sortCriterion == criterion) {
                                            sortAscending = !sortAscending
                                        } else {
                                            sortCriterion = criterion
                                            sortAscending = true
                                        }
                                        showSortMenu = false
                                    },
                                    onToggleLayout = {
                                        onSetLayout(
                                            if (state.layout == BrowserLayout.TILES) {
                                                BrowserLayout.DEFAULT_TABLE
                                            } else {
                                                BrowserLayout.TILES
                                            },
                                        )
                                    },
                                )
                                if (state.searchQuery.isNotBlank() && state.isRemoteSearchLoading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                TransferSummary(state.transfers)
                            }
                        }
                    }
                }
            },
            bottomBar = {
                BrowserBottomNavigation(
                    selectedDestination = selectedDestination,
                    onPersonal = {
                        selectedDestination = FileBrowserDestination.Personal
                        state.spaces.firstOrNull { it.type == "personal" }?.let { onSelectSpace(it.driveId) }
                    },
                    onFavorites = { selectedDestination = FileBrowserDestination.Favorites },
                    onUnavailable = onGlobalAction,
                )
            },
            floatingActionButton = {
                if (!selectionMode && selectedDestination == FileBrowserDestination.Personal) {
                    FloatingActionButton(onClick = { dialog = BrowserDialog.New }) {
                        Icon(Icons.Default.Add, contentDescription = "New")
                    }
                }
            },
        ) { padding ->
            if (selectedDestination == FileBrowserDestination.Favorites) {
                FavoritesContent(
                    state = favoritesState,
                    contentPadding = padding,
                    onRemove = onRemoveFavorite,
                    onDismissError = onDismissFavoriteError,
                )
            } else if (state.layout == BrowserLayout.TILES) {
                BrowserGrid(
                    resources = visibleResources,
                    selectedIds = state.selectedIds,
                    selectionMode = selectionMode,
                    contentPadding = padding,
                    onOpen = onOpen,
                    onToggleSelection = onToggleSelection,
                    onShowActions = onShowActions,
                )
            } else {
                BrowserList(
                    resources = visibleResources,
                    selectedIds = state.selectedIds,
                    selectionMode = selectionMode,
                    condensed = false,
                    contentPadding = padding,
                    onOpen = onOpen,
                    onToggleSelection = onToggleSelection,
                    onShowActions = onShowActions,
                )
            }
        }
    }

    state.transfers.firstOrNull { it.state == TransferState.CONFLICT.name }?.let { conflict ->
        ConflictResolutionDialog(conflict = conflict, onDecision = { onResolveConflict(conflict, it) })
    }
    state.message?.let { BrowserNotice("Notice", it, onClearMessage) }
    state.error?.let { BrowserNotice("File operation", it, onClearMessage) }
    state.actionResource?.let { resource ->
        ResourceActionSheet(
            resource = resource,
            onDismiss = onDismissActions,
            onRename = {
                dialog = BrowserDialog.Rename(resource)
                onDismissActions()
            },
            onMove = { onMove(resource) },
            onCopy = { onCopy(resource) },
            onDownloadForOffline = { onDownloadForOffline(resource) },
            onToggleFavorite = { onToggleFavorite(resource) },
            onDelete = { onDelete(resource) },
            sheetState = sheetState,
        )
    }
    when (val currentDialog = dialog) {
        BrowserDialog.New ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("New") },
                text = {
                    Column {
                        SheetAction("Upload file", Icons.Default.CloudUpload) {
                            dialog = null
                            onUpload()
                        }
                        SheetAction("Create folder", Icons.Default.Folder) { dialog = BrowserDialog.CreateFolder }
                        SheetAction("Create space", Icons.Default.Apps) { dialog = BrowserDialog.CreateSpace }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        BrowserDialog.CreateFolder ->
            NameDialog("New folder", "Create", onDismiss = { dialog = null }) { name ->
                onCreateFolder(name)
                dialog = null
            }
        BrowserDialog.CreateSpace ->
            NameDialog("New space", "Create", onDismiss = { dialog = null }) { name ->
                onCreateSpace(name)
                dialog = null
            }
        is BrowserDialog.Rename ->
            NameDialog(
                title = "Rename",
                confirm = "Save",
                onDismiss = { dialog = null },
                initial = currentDialog.resource.name,
            ) { name ->
                onRename(currentDialog.resource, name)
                dialog = null
            }
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun BrowserTopAppBar(
    accountId: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenAccount: () -> Unit,
) = TopAppBar(
    title = {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OpenCloudDimensions.SpacingXxs)
                    .semantics { contentDescription = "Filter files" },
            placeholder = { Text("Search in files") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )
    },
    navigationIcon = {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.size(OpenCloudDimensions.TouchTarget),
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Open navigation drawer")
        }
    },
    actions = {
        Box(
            modifier = Modifier.padding(end = OpenCloudDimensions.SpacingMd),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onOpenAccount,
                modifier =
                    Modifier
                        .size(OpenCloudDimensions.TouchTarget)
                        .semantics { contentDescription = "Open account information" },
            ) {
                Surface(
                    modifier = Modifier.size(OpenCloudDimensions.AvatarSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = accountId.firstOrNull()?.uppercase() ?: "A",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTopAppBar(
    accountId: String,
    onOpenDrawer: () -> Unit,
    onOpenAccount: () -> Unit,
) = TopAppBar(
    title = { Text("Favorites") },
    navigationIcon = {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(OpenCloudDimensions.TouchTarget)) {
            Icon(Icons.Default.Menu, contentDescription = "Open navigation drawer")
        }
    },
    actions = { AccountAvatar(accountId, onOpenAccount) },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
)

@Composable
private fun AccountAvatar(
    accountId: String,
    onOpenAccount: () -> Unit,
) {
    Box(
        modifier = Modifier.padding(end = OpenCloudDimensions.SpacingMd),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onOpenAccount,
            modifier =
                Modifier
                    .size(OpenCloudDimensions.TouchTarget)
                    .semantics { contentDescription = "Open account information" },
        ) {
            Surface(
                modifier = Modifier.size(OpenCloudDimensions.AvatarSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = accountId.firstOrNull()?.uppercase() ?: "A",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun BrowserSubHeader(
    canNavigateUp: Boolean,
    layout: BrowserLayout,
    sortCriterion: BrowserSortCriterion,
    sortAscending: Boolean,
    showSortMenu: Boolean,
    onNavigateUp: () -> Unit,
    onShowSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortSelect: (BrowserSortCriterion) -> Unit,
    onToggleLayout: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OpenCloudDimensions.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canNavigateUp) {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
            }
        }
        Box {
            TextButton(onClick = onShowSortMenu) {
                Text(sortCriterion.label)
                Icon(
                    imageVector =
                        if (sortAscending) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                    contentDescription = if (sortAscending) "Ascending" else "Descending",
                )
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = onDismissSortMenu) {
                BrowserSortCriterion.entries.forEach { criterion ->
                    DropdownMenuItem(
                        text = { Text(criterion.label) },
                        trailingIcon = {
                            if (criterion == sortCriterion) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        onClick = { onSortSelect(criterion) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleLayout) {
            Icon(
                imageVector =
                    if (layout == BrowserLayout.TILES) {
                        Icons.AutoMirrored.Filled.ViewList
                    } else {
                        Icons.Default.GridView
                    },
                contentDescription =
                    if (layout ==
                        BrowserLayout.TILES
                    ) {
                        "Switch to list view"
                    } else {
                        "Switch to grid view"
                    },
            )
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDownloadSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
) = TopAppBar(
    title = { Text("$selectedCount selected") },
    navigationIcon = {
        IconButton(onClick = onClearSelection) {
            Icon(Icons.Default.Close, contentDescription = "Clear selection")
        }
    },
    actions = {
        IconButton(onClick = onDownloadSelection) {
            Icon(Icons.Default.CloudDownload, contentDescription = "Download selected for offline use")
        }
        IconButton(onClick = onDeleteSelection) {
            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
)

@Composable
private fun TransferSummary(transfers: List<TransferEntity>) {
    val active = transfers.firstOrNull { it.state in VISIBLE_TRANSFER_STATES } ?: return
    val progress =
        if (active.bytesTotal > 0) {
            (active.bytesTransferred.toFloat() / active.bytesTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OpenCloudDimensions.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs),
    ) {
        Text(
            text = transferSummary(active),
            color =
                if (active.state in
                    FAILED_TRANSFER_STATES
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            style = MaterialTheme.typography.labelMedium,
        )
        if (active.state in ACTIVE_TRANSFER_STATES) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BrowserNavigationDrawer(
    releaseVersion: String,
    onTransfers: () -> Unit,
    onDeletedFiles: () -> Unit,
    onSettings: () -> Unit,
) = ModalDrawerSheet {
    Text(
        text = "OpenCloud",
        modifier = Modifier.padding(OpenCloudDimensions.SpacingXl),
        style = MaterialTheme.typography.headlineSmall,
    )
    NavigationDrawerItem(
        label = { Text("Transfers") },
        selected = false,
        onClick = onTransfers,
        icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
        modifier =
            Modifier
                .padding(horizontal = OpenCloudDimensions.SpacingSm)
                .semantics { contentDescription = "Navigate to Transfers" },
    )
    NavigationDrawerItem(
        label = { Text("Deleted files") },
        selected = false,
        onClick = onDeletedFiles,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        modifier =
            Modifier
                .padding(horizontal = OpenCloudDimensions.SpacingSm)
                .semantics { contentDescription = "Navigate to Deleted files" },
    )
    Spacer(Modifier.weight(1f))
    HorizontalDivider()
    NavigationDrawerItem(
        label = { Text("Settings") },
        selected = false,
        onClick = onSettings,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        modifier =
            Modifier
                .padding(OpenCloudDimensions.SpacingSm)
                .semantics { contentDescription = "Open Settings" },
    )
    Text(
        text = "Version $releaseVersion",
        modifier = Modifier.padding(OpenCloudDimensions.SpacingXl),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

private val ACTIVE_TRANSFER_STATES =
    setOf(TransferState.QUEUED.name, TransferState.RUNNING.name, TransferState.RETRY.name)
private val FAILED_TRANSFER_STATES = setOf(TransferState.CONFLICT.name, TransferState.FAILED.name)
private val VISIBLE_TRANSFER_STATES = ACTIVE_TRANSFER_STATES + FAILED_TRANSFER_STATES

private fun transferSummary(transfer: TransferEntity): String =
    when (transfer.state) {
        TransferState.CONFLICT.name -> "Conflict: ${transfer.displayName}"
        TransferState.FAILED.name -> "Transfer failed: ${transfer.displayName}"
        TransferState.RETRY.name -> "Retrying ${transfer.displayName}"
        else -> "${transfer.direction.lowercase().replaceFirstChar(Char::uppercase)}: ${transfer.displayName}"
    }

@Composable
private fun BrowserBottomNavigation(
    selectedDestination: FileBrowserDestination,
    onPersonal: () -> Unit,
    onFavorites: () -> Unit,
    onUnavailable: () -> Unit,
) = NavigationBar {
    FileBrowserDestination.entries.forEach { destination ->
        NavigationBarItem(
            selected = destination == selectedDestination,
            onClick =
                when (destination) {
                    FileBrowserDestination.Personal -> onPersonal
                    FileBrowserDestination.Favorites -> onFavorites
                    else -> onUnavailable
                },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = { Text(destination.label, maxLines = 1) },
            modifier = Modifier.semantics { contentDescription = "Navigate to ${destination.label}" },
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun BrowserList(
    resources: List<ResourceEntity>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    condensed: Boolean,
    contentPadding: PaddingValues,
    onOpen: (ResourceEntity) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowActions: (ResourceEntity) -> Unit,
) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = contentPadding,
) {
    items(resources, key = { it.remoteId }) { resource ->
        BrowserListItem(
            resource = resource,
            selected = resource.remoteId in selectedIds,
            selectionMode = selectionMode,
            condensed = condensed,
            onOpen = { onOpen(resource) },
            onSelect = { onToggleSelection(resource.remoteId) },
            onActions = { onShowActions(resource) },
        )
        HorizontalDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun BrowserListItem(
    resource: ResourceEntity,
    selected: Boolean,
    selectionMode: Boolean,
    condensed: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onActions: () -> Unit,
) = ListItem(
    headlineContent = { Text(resource.name, maxLines = 1) },
    supportingContent = if (condensed) null else ({ ResourceMetadata(resource) }),
    leadingContent = { ResourceIcon(resource.kind) },
    trailingContent = {
        if (selectionMode) {
            SelectionCircle(selected = selected, resourceName = resource.name, onSelect = onSelect)
        } else {
            IconButton(onClick = onActions) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${resource.name}")
            }
        }
    },
    colors =
        ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else OpenCloudColor.Transparent,
        ),
    modifier =
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onOpen() },
                onLongClick = onSelect,
            ),
)

@Composable
@Suppress("LongParameterList")
private fun BrowserGrid(
    resources: List<ResourceEntity>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    contentPadding: PaddingValues,
    onOpen: (ResourceEntity) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowActions: (ResourceEntity) -> Unit,
) = LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding =
        PaddingValues(
            start = OpenCloudDimensions.SpacingMd,
            top = contentPadding.calculateTopPadding() + OpenCloudDimensions.SpacingMd,
            end = OpenCloudDimensions.SpacingMd,
            bottom = contentPadding.calculateBottomPadding() + OpenCloudDimensions.SpacingMd,
        ),
    horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
    verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
) {
    items(resources, key = { it.remoteId }) { resource ->
        BrowserGridItem(
            resource = resource,
            selected = resource.remoteId in selectedIds,
            selectionMode = selectionMode,
            onOpen = { onOpen(resource) },
            onSelect = { onToggleSelection(resource.remoteId) },
            onActions = { onShowActions(resource) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun BrowserGridItem(
    resource: ResourceEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onActions: () -> Unit,
) = Card(
    modifier =
        Modifier.combinedClickable(
            onClick = { if (selectionMode) onSelect() else onOpen() },
            onLongClick = onSelect,
        ),
    colors =
        CardDefaults.cardColors(
            containerColor =
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
        ),
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(OpenCloudDimensions.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ResourceIcon(resource.kind, Modifier.size(OpenCloudDimensions.TouchTarget))
            Spacer(Modifier.weight(1f))
            if (selectionMode) {
                SelectionCircle(selected = selected, resourceName = resource.name, onSelect = onSelect)
            } else {
                IconButton(onClick = onActions) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${resource.name}")
                }
            }
        }
        Text(resource.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        ResourceMetadata(resource)
    }
}

@Composable
private fun SelectionCircle(
    selected: Boolean,
    resourceName: String,
    onSelect: () -> Unit,
) {
    IconButton(onClick = onSelect) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (selected) "Deselect $resourceName" else "Select $resourceName",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResourceIcon(
    kind: ResourceKind,
    modifier: Modifier = Modifier,
) {
    val icon =
        if (kind == ResourceKind.FOLDER) {
            Icons.Default.Folder
        } else {
            Icons.AutoMirrored.Filled.InsertDriveFile
        }
    val description = if (kind == ResourceKind.FOLDER) "Folder" else "File"
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = modifier,
        tint =
            if (kind ==
                ResourceKind.FOLDER
            ) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
    )
}

@Composable
private fun ResourceMetadata(resource: ResourceEntity) {
    val availableOffline = resource.hasLocalCopy || resource.offlinePinned
    Row(
        horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            resourceMetadata(resource),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Icon(
            imageVector = if (availableOffline) Icons.Default.Smartphone else Icons.Default.CloudDownload,
            contentDescription = if (availableOffline) "Available offline" else "Cloud only",
            modifier = Modifier.size(OpenCloudDimensions.SpacingMd),
            tint =
                if (availableOffline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) = ListItem(
    headlineContent = { Text(label, color = color) },
    leadingContent = { Icon(icon, contentDescription = null, tint = color) },
    colors = ListItemDefaults.colors(containerColor = OpenCloudColor.Transparent),
    modifier = Modifier.fillMaxWidth().combinedClickable(role = Role.Button, onClick = onClick),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun ResourceActionSheet(
    resource: ResourceEntity,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDownloadForOffline: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = OpenCloudDimensions.SpacingXl)) {
            Text(
                resource.name,
                modifier = Modifier.padding(OpenCloudDimensions.SpacingMd),
                style = MaterialTheme.typography.titleLarge,
            )
            SheetAction("Rename", Icons.AutoMirrored.Filled.InsertDriveFile, onClick = onRename)
            SheetAction("Move here", Icons.Default.Folder, onClick = onMove)
            SheetAction("Copy here", Icons.AutoMirrored.Filled.InsertDriveFile, onClick = onCopy)
            SheetAction(
                "Download for offline use",
                Icons.Default.CloudDownload,
                onClick = onDownloadForOffline,
            )
            SheetAction(
                if (resource.isFavorite) "Remove from favorites" else "Add to favorites",
                Icons.Default.Star,
                onClick = onToggleFavorite,
            )
            SheetAction("Delete", Icons.Default.Delete, MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

@Composable
private fun BrowserNotice(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
)

@Composable
private fun ConflictResolutionDialog(
    conflict: TransferEntity,
    onDecision: (ConflictDecision) -> Unit,
) = AlertDialog(
    onDismissRequest = {},
    title = { Text("Upload conflict") },
    text = { Text("${conflict.displayName} already exists. Choose which version to keep.") },
    confirmButton = { TextButton(onClick = { onDecision(ConflictDecision.REPLACE) }) { Text("Replace remote") } },
    dismissButton = {
        Row {
            TextButton(onClick = { onDecision(ConflictDecision.KEEP_BOTH) }) { Text("Keep both") }
            TextButton(onClick = { onDecision(ConflictDecision.CANCEL) }) { Text("Cancel upload") }
        }
    },
)

@Composable
@Suppress("LongParameterList")
fun FolderBackupSettingsDialog(
    backups: List<FolderBackupEntity>,
    pickerTrail: List<BackupFolderCrumb>,
    pickerFolders: List<ResourceEntity>,
    onDismiss: () -> Unit,
    onAdd: (BackupDraft) -> Unit,
    onDelete: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenFolder: (ResourceEntity) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    text = {
        FolderBackupSettingsContent(
            backups = backups,
            pickerTrail = pickerTrail,
            pickerFolders = pickerFolders,
            onDismiss = onDismiss,
            onAdd = onAdd,
            onDelete = onDelete,
            onOpenPicker = onOpenPicker,
            onOpenFolder = onOpenFolder,
            onNavigateUp = onNavigateUp,
            onCreateFolder = onCreateFolder,
        )
    },
    confirmButton = {},
)

@Composable
@Suppress("LongParameterList")
fun FolderBackupSettingsContent(
    backups: List<FolderBackupEntity>,
    onDismiss: () -> Unit,
    onAdd: (BackupDraft) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    pickerTrail: List<BackupFolderCrumb> = emptyList(),
    pickerFolders: List<ResourceEntity> = emptyList(),
    onOpenPicker: () -> Unit = {},
    onOpenFolder: (ResourceEntity) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
) {
    var destination by remember { mutableStateOf("/Camera Uploads") }
    var showDestinationPicker by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var mediaType by remember { mutableStateOf("IMAGE") }
    var wifiOnly by remember { mutableStateOf(true) }
    var chargingOnly by remember { mutableStateOf(false) }
    var deleteAfterUpload by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
    ) {
        Text("Folder & camera backup", style = MaterialTheme.typography.headlineSmall)
        Text("Remote destination", style = MaterialTheme.typography.labelLarge)
        Text(destination, style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = {
                onOpenPicker()
                showDestinationPicker = true
            },
        ) { Text("Select folder") }
        Text("File type", style = MaterialTheme.typography.labelLarge)
        Row {
            listOf("IMAGE" to "Photos", "VIDEO" to "Videos", "ALL" to "All files").forEach { (value, label) ->
                TextButton(onClick = { mediaType = value }) {
                    Text(
                        if (mediaType == value) {
                            "✓ $label"
                        } else {
                            label
                        },
                    )
                }
            }
        }
        BackupCheckbox("Wi-Fi only", wifiOnly) { wifiOnly = it }
        BackupCheckbox("Only while charging", chargingOnly) { chargingOnly = it }
        BackupCheckbox("Delete local file after upload", deleteAfterUpload) { deleteAfterUpload = it }
        HorizontalDivider()
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { contentDescription = "Active backup configurations" },
        ) {
            items(backups, key = { it.id }) { backup ->
                BackupConfigurationItem(backup = backup, onDelete = { onDelete(backup.id) })
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Done") }
            TextButton(
                onClick = {
                    onAdd(BackupDraft(destination, mediaType, wifiOnly, chargingOnly, deleteAfterUpload))
                },
            ) {
                Text("Choose source folder")
            }
        }
    }
    if (showDestinationPicker) {
        RemoteFolderPickerDialog(
            trail = pickerTrail,
            folders = pickerFolders,
            onDismiss = { showDestinationPicker = false },
            onOpenFolder = onOpenFolder,
            onNavigateUp = onNavigateUp,
            onCreateFolder = { showCreateFolder = true },
            onSelect = {
                destination = pickerTrail.lastOrNull()?.path ?: "/"
                showDestinationPicker = false
            },
        )
    }
    if (showCreateFolder) {
        NameDialog(
            title = "New destination folder",
            confirm = "Create",
            onDismiss = { showCreateFolder = false },
        ) { name ->
            onCreateFolder(name)
            showCreateFolder = false
        }
    }
}

@Composable
private fun BackupConfigurationItem(
    backup: FolderBackupEntity,
    onDelete: () -> Unit,
) = ListItem(
    headlineContent = {
        BackupPathLine(
            label = "Local:",
            value = backup.sourceDisplayName.ifBlank { sourceNameFromTreeUri(backup.sourceTreeUri) },
        )
    },
    supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs)) {
            BackupPathLine(label = "Remote:", value = backup.destinationPath)
            Text(
                backupDetails(backup),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    },
    trailingContent = {
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove backup") }
    },
)

@Composable
private fun BackupPathLine(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}

internal fun backupDetails(backup: FolderBackupEntity): String {
    val fileType =
        when (backup.mediaType) {
            "IMAGE" -> "Photos"
            "VIDEO" -> "Videos"
            else -> "All files"
        }
    val constraints =
        buildList {
            if (backup.wifiOnly) add("Wi-Fi only")
            if (backup.chargingOnly) add("Charging")
        }.ifEmpty { listOf("No restrictions") }
    return "$fileType • ${constraints.joinToString(" • ")}"
}

@Composable
@Suppress("LongParameterList")
private fun RemoteFolderPickerDialog(
    trail: List<BackupFolderCrumb>,
    folders: List<ResourceEntity>,
    onDismiss: () -> Unit,
    onOpenFolder: (ResourceEntity) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: () -> Unit,
    onSelect: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select remote folder") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs)) {
            Text(
                trail.lastOrNull()?.path ?: "/",
                style = MaterialTheme.typography.bodySmall,
            )
            if (trail.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text("Up") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onNavigateUp, onLongClick = {}),
                )
            }
            if (folders.isEmpty()) {
                Text("No folders here", style = MaterialTheme.typography.bodyMedium)
            } else {
                folders.forEach { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { onOpenFolder(folder) }, onLongClick = {}),
                    )
                }
            }
            TextButton(onClick = onCreateFolder) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("New folder")
            }
        }
    },
    confirmButton = { TextButton(onClick = onSelect) { Text("Select this folder") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
)

@Composable
private fun BackupCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun NameDialog(
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    initial: String = "",
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun resourceMetadata(resource: ResourceEntity): String =
    if (resource.kind == ResourceKind.FOLDER) "Folder" else formatBytes(resource.sizeBytes)

private fun formatBytes(bytes: Long): String =
    if (bytes < 1_000_000) "${bytes / 1_000} KB" else "${bytes / 1_000_000} MB"

private sealed interface BrowserDialog {
    data object New : BrowserDialog

    data object CreateFolder : BrowserDialog

    data object CreateSpace : BrowserDialog

    data class Rename(
        val resource: ResourceEntity,
    ) : BrowserDialog
}

data class BackupDraft(
    val destinationPath: String,
    val mediaType: String,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val deleteAfterUpload: Boolean,
)

private enum class BrowserSortCriterion(
    val label: String,
) {
    Name("Name"),
    DateModified("Date modified"),
    DateOpened("Date opened"),
    Size("Size"),
    ;

    fun comparator(ascending: Boolean): Comparator<ResourceEntity> {
        val comparator =
            when (this) {
                Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { resource: ResourceEntity -> resource.name }
                DateModified -> compareBy<ResourceEntity> { it.modifiedAtEpochMillis }
                DateOpened -> compareBy<ResourceEntity> { it.createdAtEpochMillis }
                Size -> compareBy<ResourceEntity> { it.sizeBytes }
            }
        return if (ascending) comparator else comparator.reversed()
    }
}

enum class FileBrowserDestination(
    val label: String,
    val icon: ImageVector,
) {
    Personal("Personal", Icons.Default.Folder),
    Favorites("Favorites", Icons.Default.Star),
    Shares("Shares", Icons.Default.Share),
    Spaces("Spaces", Icons.Default.Apps),
}
