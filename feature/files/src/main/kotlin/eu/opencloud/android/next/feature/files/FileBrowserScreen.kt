package eu.opencloud.android.next.feature.files

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalRippleConfiguration
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.datastore.FileDisplayOptions
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudColor
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.model.ResourceKind
import kotlinx.coroutines.launch

@Composable
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
fun FileBrowserRoute(
    accountId: String,
    releaseVersion: String,
    destinations: FileBrowserDestinations,
    sharesContent: @Composable (PaddingValues, (ResourceEntity) -> Unit) -> Unit,
    spacesContent: @Composable (PaddingValues, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    openAddMenuRequest: Int = 0,
    onConsumeAddMenuRequest: () -> Unit = {},
    viewModel: FileBrowserViewModel = viewModel(key = "files-$accountId"),
    favoritesViewModel: FavoritesViewModel = viewModel(key = "favorites-$accountId"),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
    val exportFile = rememberDeviceExportLauncher(accountId, viewModel, state.exporting)
    val openFile = rememberFileOpener(viewModel)
    val uploadLauncher = rememberFileUploadLauncher(viewModel)
    var scanTarget by rememberSaveable(accountId) { mutableStateOf<ArrayList<String>?>(null) }
    var editorTarget by rememberSaveable(accountId) { mutableStateOf<ArrayList<String>?>(null) }
    var webAppTarget by remember(accountId) { mutableStateOf<ResourceEntity?>(null) }
    viewModel.load(accountId)
    favoritesViewModel.load(accountId)
    scanTarget?.let { target ->
        ScannerRoute(target, onClose = { scanTarget = null })
        return
    }
    Box(modifier) {
        FileBrowserScreen(
            accountId = accountId,
            releaseVersion = releaseVersion,
            openAddMenuRequest = openAddMenuRequest,
            onConsumeAddMenuRequest = onConsumeAddMenuRequest,
            state = state,
            favoritesState = favoritesState,
            onSelectSpace = viewModel::selectSpace,
            onOpen = { if (it.kind == ResourceKind.FOLDER) viewModel.open(it) else openFile(it, ExternalAction.OPEN) },
            onPrepareDetails = viewModel::prepareExternalFile,
            onOpenWith = { openFile(it, ExternalAction.OPEN_WITH) },
            onOpenWebApp = { webAppTarget = it },
            onEditText = { editorTarget = arrayListOf(it.spaceId, it.remoteId) },
            onSend = { openFile(it, ExternalAction.SEND) },
            onExport = exportFile,
            onDismissExportStatus = viewModel::dismissExportStatus,
            onRefresh = viewModel::refresh,
            onMoveSelection = viewModel::moveSelection,
            onCopySelection = viewModel::copySelection,
            onFavoriteSelection = viewModel::favoriteSelection,
            onRemoveLocalSelection = viewModel::removeSelectedLocalCopies,
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
            operationControls = {
                FileOperationControls(
                    state,
                    viewModel::place,
                    viewModel::cancelPlacement,
                    viewModel::retryOperation,
                    viewModel::dismissOperation,
                )
            },
            onDelete = viewModel::delete,
            onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
            onScan = {
                state.spaceId?.let { space ->
                    scanTarget =
                        arrayListOf(
                            accountId,
                            space,
                            state.folderTrail.joinToString("/", "/") {
                                it.name
                            },
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        )
                }
            },
            onDownloadForOffline = viewModel::downloadForOffline,
            onRemoveLocalCopy = viewModel::removeLocalCopy,
            onToggleFavorite = viewModel::toggleFavorite,
            onResolveConflict = viewModel::resolveConflict,
            onClearMessage = viewModel::clearMessage,
            onSearchQueryChange = viewModel::setSearchQuery,
            onOpenTransfers = destinations.onOpenTransfers,
            onRemoveFavorite = favoritesViewModel::remove,
            onDismissFavoriteError = favoritesViewModel::dismissError,
            onRefreshFavorites = favoritesViewModel::refresh,
            onOpenDeletedFiles = destinations.onOpenDeletedFiles,
            onOpenSettings = destinations.onOpenSettings,
            onOpenAccount = destinations.onOpenAccount,
            onShareResource = destinations.onShareResource,
            sharesContent = sharesContent,
            onBrowseShare = viewModel::browseSharedResource,
            spacesContent = spacesContent,
        )
        editorTarget?.let { target ->
            TextEditorRoute(accountId, target[0], target[1], onClose = { editorTarget = null })
        }
        webAppTarget?.let { resource ->
            androidx.compose.runtime.key(resource.accountId, resource.spaceId, resource.remoteId) {
                ServerWebAppDialog(resource, onDismiss = { webAppTarget = null })
            }
        }
    }
}

@Composable
private fun rememberFileUploadLauncher(
    viewModel: FileBrowserViewModel,
): androidx.activity.result.ActivityResultLauncher<Array<String>> {
    val context = LocalContext.current
    val mediaPermission = rememberOriginalMediaPermission { files -> files.forEach(viewModel::upload) }
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            mediaPermission(listOf(selected))
        }
    }
}

data class FileBrowserDestinations(
    val onOpenTransfers: () -> Unit,
    val onOpenDeletedFiles: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAccount: () -> Unit,
    val onShareResource: (ResourceEntity) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ComposableParamOrder", "CyclomaticComplexMethod", "LongMethod", "LongParameterList")
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
    onSearchQueryChange: (String) -> Unit,
    onOpenTransfers: () -> Unit,
    onRemoveFavorite: (ResourceEntity) -> Unit = {},
    onDismissFavoriteError: () -> Unit = {},
    onRefreshFavorites: () -> Unit = {},
    onOpenDeletedFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onShareResource: (ResourceEntity) -> Unit,
    sharesContent: @Composable (PaddingValues, (ResourceEntity) -> Unit) -> Unit = { _, _ -> },
    spacesContent: @Composable (PaddingValues, (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    initialDestination: FileBrowserDestination = FileBrowserDestination.Personal,
    openAddMenuRequest: Int = 0,
    onConsumeAddMenuRequest: () -> Unit = {},
    operationControls: @Composable () -> Unit = {},
    onOpenWith: (ResourceEntity) -> Unit = {},
    onOpenWebApp: (ResourceEntity) -> Unit = {},
    onSend: (ResourceEntity) -> Unit = {},
    onPrepareDetails: (suspend (ResourceEntity) -> ResourceEntity)? = null,
    onEditText: (ResourceEntity) -> Unit = {},
    onRefresh: () -> Unit = {},
    onMoveSelection: () -> Unit = {},
    onCopySelection: () -> Unit = {},
    onFavoriteSelection: () -> Unit = {},
    onRemoveLocalSelection: () -> Unit = {},
    onBrowseShare: (ResourceEntity) -> Unit = {},
    onExport: (ResourceEntity, Boolean) -> Unit = { _, _ -> },
    onDismissExportStatus: () -> Unit = {},
    onRemoveLocalCopy: (ResourceEntity) -> Unit = {},
    onScan: () -> Unit = {},
) {
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var offlineFilter by rememberSaveable(accountId) { mutableStateOf(OfflineFilter.ALL) }
    var details by remember { mutableStateOf<ResourceEntity?>(null) }
    var expandedAdd by rememberSaveable { mutableStateOf(false) }
    var sortCriterion by rememberSaveable(accountId) { mutableStateOf(BrowserSortCriterion.Name) }
    var sortAscending by rememberSaveable(accountId) { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var favoritesQuery by rememberSaveable(accountId) { mutableStateOf("") }
    // Keep the in-session tab selection through recomposition, but start a fresh app launch
    // on Personal instead of restoring a previously selected Space.
    var selectedDestination by remember(accountId) { mutableStateOf(initialDestination) }
    var appliedPersonalRequest by remember(accountId) { mutableIntStateOf(0) }
    val currentOnSelectSpace by rememberUpdatedState(onSelectSpace)
    val currentOnConsumeAddMenuRequest by rememberUpdatedState(onConsumeAddMenuRequest)
    LaunchedEffect(openAddMenuRequest) {
        if (openAddMenuRequest > 0) {
            selectedDestination = FileBrowserDestination.Personal
            expandedAdd = true
        }
    }
    LaunchedEffect(openAddMenuRequest, state.spaces) {
        if (openAddMenuRequest > appliedPersonalRequest) {
            state.spaces.firstOrNull { it.type.equals("personal", ignoreCase = true) }?.let { personal ->
                currentOnSelectSpace(personal.driveId)
                appliedPersonalRequest = openAddMenuRequest
                currentOnConsumeAddMenuRequest()
            }
        }
    }
    val activeProjectSpace = state.spaces.firstOrNull { it.driveId == state.spaceId && it.type == "project" }
    val browsingProjectSpace = selectedDestination == FileBrowserDestination.Personal && activeProjectSpace != null
    val browserDestination =
        selectedDestination == FileBrowserDestination.Personal ||
            selectedDestination == FileBrowserDestination.Favorites ||
            selectedDestination == FileBrowserDestination.Offline ||
            selectedDestination == FileBrowserDestination.Recents
    val selectionMode =
        selectedDestination in setOf(FileBrowserDestination.Personal, FileBrowserDestination.Offline) &&
            state.selectedIds.isNotEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val visibleResources =
        remember(state.resources, state.searchQuery, state.searchResults, sortCriterion, sortAscending) {
            val source = if (state.searchQuery.isBlank()) state.resources else state.searchResults
            source
                .sortedWith(sortCriterion.comparator(sortAscending))
        }
    val visibleFavorites =
        remember(favoritesState.resources, favoritesQuery, sortCriterion, sortAscending) {
            favoritesState.resources
                .filter { resource ->
                    favoritesQuery.isBlank() || resource.name.contains(favoritesQuery, ignoreCase = true)
                }.sortedWith(sortCriterion.comparator(sortAscending))
        }

    BackHandler(
        enabled =
            drawerState.isOpen ||
                selectionMode ||
                expandedAdd ||
                state.searchQuery.isNotEmpty() ||
                selectedDestination != FileBrowserDestination.Personal ||
                state.folderTrail.isNotEmpty() ||
                browsingProjectSpace,
    ) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            expandedAdd -> expandedAdd = false
            selectionMode -> onClearSelection()
            state.searchQuery.isNotEmpty() -> onSearchQueryChange("")
            selectedDestination != FileBrowserDestination.Personal ->
                selectedDestination =
                    FileBrowserDestination.Personal
            browsingProjectSpace && state.folderTrail.isEmpty() -> selectedDestination = FileBrowserDestination.Spaces
            else -> onNavigateUp()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !selectionMode,
        drawerContent = {
            BrowserNavigationDrawer(
                releaseVersion = releaseVersion,
                selectedDestination = selectedDestination,
                onRecents = {
                    onClearSelection()
                    selectedDestination = FileBrowserDestination.Recents
                    scope.launch { drawerState.close() }
                },
                onOffline = {
                    onClearSelection()
                    selectedDestination = FileBrowserDestination.Offline
                    scope.launch { drawerState.close() }
                },
                onShares = {
                    onClearSelection()
                    selectedDestination = FileBrowserDestination.Shares
                    scope.launch { drawerState.close() }
                },
                personalSpace = state.spaces.firstOrNull { it.type == "personal" },
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
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SelectionAction(
                                stringResource(R.string.browser_move),
                                Icons.AutoMirrored.Filled.DriveFileMove,
                                stringResource(R.string.browser_move_selected),
                                onMoveSelection,
                            )
                            SelectionAction(
                                stringResource(R.string.browser_copy),
                                Icons.Default.ContentCopy,
                                stringResource(R.string.browser_copy_selected),
                                onCopySelection,
                            )
                            SelectionAction(
                                stringResource(R.string.browser_favorite),
                                Icons.Default.Star,
                                stringResource(R.string.browser_favorite_selected),
                                onFavoriteSelection,
                            )
                            SelectionAction(
                                stringResource(R.string.browser_delete),
                                Icons.Default.Delete,
                                stringResource(R.string.browser_delete_selected),
                                onDeleteSelection,
                            )
                            if ((state.resources + state.searchResults + state.offlineResources).any {
                                    it.selectionKey in state.selectedIds &&
                                        it.hasLocalCopy
                                }
                            ) {
                                SelectionAction(
                                    stringResource(R.string.browser_clear_local),
                                    cleanupIcon(),
                                    stringResource(R.string.browser_delete_local_copies),
                                    onRemoveLocalSelection,
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                if (browsingProjectSpace) {
                                    ProjectSpaceHeader(requireNotNull(activeProjectSpace).name, state.folderTrail) {
                                        onClearSelection()
                                        selectedDestination = FileBrowserDestination.Spaces
                                    }
                                }
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
                                        onSetLayout(state.layout.next())
                                    },
                                )
                                TransferSummary(state.transfers, onOpenTransfers)
                                ExportStatus(state, onDismissExportStatus)
                            }
                        }
                    }
                } else if (browserDestination) {
                    Column {
                        BrowserTopAppBar(
                            accountId = accountId,
                            query =
                                if (selectedDestination == FileBrowserDestination.Favorites) {
                                    favoritesQuery
                                } else {
                                    state.searchQuery
                                },
                            onQueryChange =
                                if (selectedDestination == FileBrowserDestination.Favorites) {
                                    { favoritesQuery = it }
                                } else {
                                    onSearchQueryChange
                                },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenAccount = onOpenAccount,
                        )
                        if (selectedDestination in
                            setOf(FileBrowserDestination.Offline, FileBrowserDestination.Recents)
                        ) {
                            Text(
                                stringResource(selectedDestination.labelResource),
                                Modifier.padding(horizontal = OpenCloudDimensions.SpacingMd),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                if (browsingProjectSpace) {
                                    ProjectSpaceHeader(requireNotNull(activeProjectSpace).name, state.folderTrail) {
                                        onClearSelection()
                                        selectedDestination = FileBrowserDestination.Spaces
                                    }
                                }
                                BrowserSubHeader(
                                    canNavigateUp =
                                        selectedDestination == FileBrowserDestination.Personal &&
                                            state.folderTrail.isNotEmpty(),
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
                                        onSetLayout(state.layout.next())
                                    },
                                )
                                if (
                                    selectedDestination == FileBrowserDestination.Personal &&
                                    state.searchQuery.isNotBlank() &&
                                    state.isRemoteSearchLoading
                                ) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                if (selectedDestination == FileBrowserDestination.Personal) {
                                    TransferSummary(state.transfers, onOpenTransfers)
                                    ExportStatus(state, onDismissExportStatus)
                                    state.discoveryError?.let { message ->
                                        Text(
                                            stringResource(R.string.browser_refresh_failed, message),
                                            modifier = Modifier.padding(horizontal = OpenCloudDimensions.SpacingMd),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                if (selectedDestination == FileBrowserDestination.Favorites) {
                                    FavoritesSyncNotice(
                                        favoritesState.syncStatus,
                                        onRefreshFavorites,
                                        favoritesState.refreshError,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    DestinationTopAppBar(
                        title = stringResource(selectedDestination.labelResource),
                        accountId = accountId,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenAccount = onOpenAccount,
                    )
                }
            },
            bottomBar = {
                Column {
                    operationControls()
                    BrowserBottomNavigation(
                        selectedDestination =
                            if (browsingProjectSpace) FileBrowserDestination.Spaces else selectedDestination,
                        onPersonal = {
                            onClearSelection()
                            selectedDestination = FileBrowserDestination.Personal
                            state.spaces.firstOrNull { it.type == "personal" }?.let { onSelectSpace(it.driveId) }
                        },
                        onFavorites = {
                            onClearSelection()
                            selectedDestination = FileBrowserDestination.Favorites
                        },
                        onSpaces = {
                            onClearSelection()
                            selectedDestination = FileBrowserDestination.Spaces
                        },
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
                    ) {
                        if (expandedAdd) {
                            val uploadActionLabel = stringResource(R.string.browser_upload_file)
                            val scanActionLabel = stringResource(R.string.browser_scan)
                            val destinationSpace = state.spaces.firstOrNull { it.driveId == state.spaceId }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                TextButton(onClick = { selectedDestination = FileBrowserDestination.Personal }) {
                                    Text(
                                        stringResource(
                                            R.string.browser_add_destination,
                                            (listOfNotNull(destinationSpace?.name) + state.folderTrail.map { it.name })
                                                .joinToString(" / "),
                                        ),
                                    )
                                }
                            }
                            ExtendedFloatingActionButton(
                                onClick = {
                                    expandedAdd = false
                                    onUpload()
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        null,
                                    )
                                },
                                text = { Text(stringResource(R.string.browser_upload_file)) },
                                modifier =
                                    Modifier.semantics { contentDescription = uploadActionLabel },
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    expandedAdd = false
                                    onScan()
                                },
                                icon = { Icon(Icons.Default.CameraAlt, null) },
                                text = { Text(scanActionLabel) },
                                modifier = Modifier.semantics { contentDescription = scanActionLabel },
                            )
                            ExtendedFloatingActionButton(onClick = {
                                expandedAdd = false
                                dialog =
                                    BrowserDialog.CreateFolder
                            }, icon = {
                                Icon(
                                    Icons.Default.Folder,
                                    null,
                                )
                            }, text = { Text(stringResource(R.string.browser_create_folder)) })
                            ExtendedFloatingActionButton(onClick = {
                                expandedAdd = false
                                dialog =
                                    BrowserDialog.CreateSpace
                            }, icon = {
                                Icon(
                                    Icons.Default.Apps,
                                    null,
                                )
                            }, text = { Text(stringResource(R.string.browser_create_space)) })
                        }
                        FloatingActionButton(onClick = { expandedAdd = !expandedAdd }) {
                            Icon(
                                if (expandedAdd) Icons.Default.Close else Icons.Default.Add,
                                contentDescription =
                                    stringResource(
                                        if (expandedAdd) R.string.browser_close_new_actions else R.string.browser_new,
                                    ),
                            )
                        }
                    }
                }
            },
        ) { outerPadding ->
            if (selectedDestination == FileBrowserDestination.Shares) {
                sharesContent(outerPadding) { resource ->
                    onClearSelection()
                    selectedDestination = FileBrowserDestination.Personal
                    onBrowseShare(resource)
                }
            } else if (selectedDestination == FileBrowserDestination.Spaces) {
                spacesContent(outerPadding) { spaceId ->
                    onClearSelection()
                    selectedDestination = FileBrowserDestination.Personal
                    onSelectSpace(spaceId)
                }
            } else {
                PullToRefreshBox(
                    isRefreshing =
                        if (selectedDestination ==
                            FileBrowserDestination.Favorites
                        ) {
                            favoritesState.syncStatus == FavoritesSyncStatus.RUNNING
                        } else {
                            delayedRefreshIndicator(state.refreshing, state.spaceId to state.currentFolderId)
                        },
                    onRefresh =
                        if (selectedDestination ==
                            FileBrowserDestination.Favorites
                        ) {
                            onRefreshFavorites
                        } else {
                            onRefresh
                        },
                    modifier = Modifier.fillMaxSize().padding(outerPadding),
                ) {
                    val padding = PaddingValues(OpenCloudDimensions.Zero)
                    val resources =
                        if (selectedDestination == FileBrowserDestination.Favorites) {
                            visibleFavorites
                        } else if (selectedDestination == FileBrowserDestination.Offline) {
                            state.offlineResources
                                .filter {
                                    it.name.contains(state.searchQuery, true) &&
                                        offlineFilter.matches(it, state.offlinePins)
                                }.sortedWith(sortCriterion.comparator(sortAscending))
                        } else if (selectedDestination == FileBrowserDestination.Recents) {
                            state.recentResources.filter { it.name.contains(state.searchQuery, true) }
                        } else {
                            visibleResources
                        }
                    val displayedResources =
                        resources.filter {
                            state.fileDisplay.showHidden ||
                                !it.name.startsWith(
                                    ".",
                                )
                        }
                    Column(Modifier.fillMaxSize()) {
                        if (selectedDestination == FileBrowserDestination.Offline) {
                            OfflineHeader(state.offlineBytes, offlineFilter) {
                                offlineFilter = it
                                onClearSelection()
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            if (selectedDestination == FileBrowserDestination.Favorites &&
                                displayedResources.isEmpty()
                            ) {
                                FavoritesEmpty(contentPadding = padding)
                            } else if (displayedResources.isEmpty() &&
                                selectedDestination in
                                setOf(FileBrowserDestination.Offline, FileBrowserDestination.Recents)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (selectedDestination ==
                                            FileBrowserDestination.Offline
                                        ) {
                                            stringResource(R.string.browser_no_downloaded_files)
                                        } else {
                                            stringResource(R.string.browser_opened_files_offline)
                                        },
                                    )
                                }
                            } else if (state.layout == BrowserLayout.TILES) {
                                BrowserGrid(
                                    offlinePins = state.offlinePins,
                                    resources = displayedResources,
                                    display = state.fileDisplay,
                                    selectedIds = state.selectedIds,
                                    selectionMode = selectionMode,
                                    contentPadding = padding,
                                    onOpen = onOpen,
                                    onToggleSelection = onToggleSelection,
                                    onShowActions = onShowActions,
                                )
                            } else {
                                BrowserList(
                                    offlinePins = state.offlinePins,
                                    resources = displayedResources,
                                    display = state.fileDisplay,
                                    selectedIds = state.selectedIds,
                                    selectionMode = selectionMode,
                                    condensed = state.layout == BrowserLayout.CONDENSED_TABLE,
                                    contentPadding = padding,
                                    onOpen = onOpen,
                                    onToggleSelection = onToggleSelection,
                                    onShowActions = onShowActions,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.transfers.firstOrNull { it.state == TransferState.CONFLICT.name }?.let { conflict ->
        ConflictResolutionDialog(conflict = conflict, onDecision = { onResolveConflict(conflict, it) })
    }
    state.message?.let { BrowserNotice(stringResource(R.string.browser_notice), it, onClearMessage) }
    details?.let { resource ->
        ResourceDetailsDialog(
            resource,
            isKeptOffline(
                resource,
                state.offlinePins,
            ),
            state.temporaryCopyRetentionHours,
            onDismiss = {
                details =
                    null
            },
            prepareFile = onPrepareDetails,
        )
    }
    state.error?.let { BrowserNotice(stringResource(R.string.browser_file_operation), it, onClearMessage) }
    favoritesState.error?.let { BrowserNotice(stringResource(R.string.browser_favorites), it, onDismissFavoriteError) }
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
            keptOffline = isKeptOffline(resource, state.offlinePins),
            onRemoveLocalCopy = { onRemoveLocalCopy(resource) },
            onToggleFavorite = {
                if (selectedDestination == FileBrowserDestination.Favorites) {
                    onRemoveFavorite(resource)
                } else {
                    onToggleFavorite(resource)
                }
            },
            onShare = {
                onDismissActions()
                onShareResource(resource)
            },
            onDelete = { onDelete(resource) },
            sheetState = sheetState,
            onEditText =
                if (canEditText(resource)) {
                    {
                        onDismissActions()
                        onEditText(resource)
                    }
                } else {
                    null
                },
            onOpenWith =
                if (resource.kind ==
                    ResourceKind.FILE
                ) {
                    (
                        {
                            onDismissActions()
                            onOpenWith(resource)
                        }
                    )
                } else {
                    null
                },
            onSend =
                if (resource.kind == ResourceKind.FILE) {
                    (
                        {
                            onDismissActions()
                            onSend(resource)
                        }
                    )
                } else {
                    null
                },
            onOpenWebApp =
                if (resource.kind == ResourceKind.FILE) {
                    {
                        onDismissActions()
                        onOpenWebApp(resource)
                    }
                } else {
                    null
                },
            onDetails = {
                onDismissActions()
                details = resource
            },
            onExport =
                if (resource.kind == ResourceKind.FILE) {
                    { move ->
                        onDismissActions()
                        onExport(resource, move)
                    }
                } else {
                    null
                },
        )
    }
    when (val currentDialog = dialog) {
        BrowserDialog.New ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.browser_new)) },
                text = {
                    Column {
                        SheetAction(stringResource(R.string.browser_upload_file), Icons.Default.CloudUpload) {
                            dialog = null
                            onUpload()
                        }
                        SheetAction(stringResource(R.string.browser_create_folder), Icons.Default.Folder) {
                            dialog =
                                BrowserDialog.CreateFolder
                        }
                        SheetAction(stringResource(R.string.browser_create_space), Icons.Default.Apps) {
                            dialog =
                                BrowserDialog.CreateSpace
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { dialog = null },
                    ) { Text(stringResource(R.string.browser_cancel)) }
                },
            )
        BrowserDialog.CreateFolder ->
            NameDialog(
                stringResource(
                    R.string.browser_new_folder,
                ),
                stringResource(R.string.browser_create),
                onDismiss = {
                    dialog =
                        null
                },
            ) { name ->
                onCreateFolder(name)
                dialog = null
            }
        BrowserDialog.CreateSpace ->
            NameDialog(
                stringResource(
                    R.string.browser_new_space,
                ),
                stringResource(R.string.browser_create),
                onDismiss = {
                    dialog =
                        null
                },
            ) { name ->
                onCreateSpace(name)
                dialog = null
            }
        is BrowserDialog.Rename ->
            NameDialog(
                title = stringResource(R.string.browser_rename),
                confirm = stringResource(R.string.browser_save),
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
                    .browserDescription(R.string.browser_filter_files),
            placeholder = { Text(stringResource(R.string.browser_search_in_files)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browser_clear_search))
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
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.browser_open_navigation_drawer))
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
                        .browserDescription(R.string.browser_open_account_information),
            ) {
                Surface(
                    modifier = Modifier.size(OpenCloudDimensions.AvatarSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        eu.opencloud.android.next.core.ui
                            .ProfileAvatar(accountId)
                    }
                }
            }
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationTopAppBar(
    title: String,
    accountId: String,
    onOpenDrawer: () -> Unit,
    onOpenAccount: () -> Unit,
) = TopAppBar(
    title = { Text(title) },
    navigationIcon = {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(OpenCloudDimensions.TouchTarget)) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.browser_open_navigation_drawer))
        }
    },
    actions = { AccountAvatar(accountId, onOpenAccount) },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
)

@Composable
private fun FavoritesEmpty(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
        ) {
            Icon(Icons.Default.Star, contentDescription = null)
            Text(stringResource(R.string.browser_no_favorites_yet), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.browser_favorites_offline))
        }
    }
}

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
                    .browserDescription(R.string.browser_open_account_information),
        ) {
            Surface(
                modifier = Modifier.size(OpenCloudDimensions.AvatarSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    eu.opencloud.android.next.core.ui
                        .ProfileAvatar(accountId)
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.browser_navigate_up),
                )
            }
        }
        Box {
            TextButton(onClick = onShowSortMenu) {
                Text(stringResource(sortCriterion.labelResource))
                Icon(
                    imageVector =
                        if (sortAscending) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                    contentDescription =
                        stringResource(
                            if (sortAscending) R.string.browser_ascending else R.string.browser_descending,
                        ),
                )
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = onDismissSortMenu) {
                BrowserSortCriterion.entries.forEach { criterion ->
                    DropdownMenuItem(
                        text = { Text(stringResource(criterion.labelResource)) },
                        trailingIcon = {
                            if (criterion == sortCriterion) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.browser_selected),
                                )
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
                imageVector = layout.nextIcon(),
                contentDescription = layout.nextContentDescription(),
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
) = TopAppBar(
    title = { Text(pluralStringResource(R.plurals.browser_selected_count, selectedCount, selectedCount)) },
    navigationIcon = {
        IconButton(onClick = onClearSelection) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browser_clear_selection))
        }
    },
    actions = {
        SelectionAction(
            stringResource(R.string.browser_keep_offline),
            Icons.Default.OfflinePin,
            stringResource(R.string.browser_download_selected_offline),
            onDownloadSelection,
        )
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
)

@Composable
private fun TransferSummary(
    transfers: List<TransferEntity>,
    onOpenTransfers: () -> Unit,
) {
    val active =
        transfers.firstOrNull { it.state == TransferState.RUNNING.name }
            ?: transfers.firstOrNull { it.state in ACTIVE_TRANSFER_STATES }
            ?: transfers.firstOrNull { it.state in FAILED_TRANSFER_STATES }
            ?: return
    val progress =
        if (active.bytesTotal > 0) {
            (active.bytesTransferred.toFloat() / active.bytesTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.browser_open_transfers), onClick = onOpenTransfers)
                .padding(horizontal = OpenCloudDimensions.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs),
    ) {
        Text(
            text = browserTransferSummary(active),
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
            if (active.bytesTotal >
                0
            ) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ExportStatus(
    state: FileBrowserUiState,
    onDismiss: () -> Unit,
) {
    state.exportStatus?.let { status ->
        Column(Modifier.fillMaxWidth().padding(horizontal = OpenCloudDimensions.SpacingMd)) {
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (state.exporting) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_dismiss)) }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun BrowserNavigationDrawer(
    releaseVersion: String,
    selectedDestination: FileBrowserDestination,
    onRecents: () -> Unit,
    onOffline: () -> Unit,
    onTransfers: () -> Unit,
    onDeletedFiles: () -> Unit,
    onSettings: () -> Unit,
    onShares: () -> Unit,
    personalSpace: eu.opencloud.android.next.core.database.SpaceEntity?,
) = ModalDrawerSheet(
    modifier =
        Modifier.width(
            (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.75f).coerceAtMost(
                OpenCloudDimensions.DrawerMaxWidth,
            ),
        ),
) {
    val context = LocalContext.current
    eu.opencloud.android.next.core.designsystem.RaiunWordmark(
        Modifier.padding(OpenCloudDimensions.SpacingXl),
    )
    HorizontalDivider()
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.browser_recents)) },
        selected = selectedDestination == FileBrowserDestination.Recents,
        onClick = onRecents,
        icon = { Icon(Icons.Default.History, null) },
        modifier =
            Modifier
                .padding(horizontal = OpenCloudDimensions.SpacingSm)
                .browserDescription(R.string.browser_navigate_to, stringResource(R.string.browser_recents)),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.browser_offline)) },
        selected = selectedDestination == FileBrowserDestination.Offline,
        onClick = onOffline,
        icon = { Icon(Icons.Default.OfflinePin, null) },
        modifier =
            Modifier
                .padding(horizontal = OpenCloudDimensions.SpacingSm)
                .browserDescription(R.string.browser_navigate_to, stringResource(R.string.browser_offline)),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.browser_shares)) },
        selected = selectedDestination == FileBrowserDestination.Shares,
        onClick = onShares,
        icon = { Icon(Icons.Default.Share, null) },
        modifier =
            Modifier
                .padding(
                    horizontal = OpenCloudDimensions.SpacingSm,
                ).browserDescription(R.string.browser_navigate_to_shares),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.browser_uploads)) },
        selected = false,
        onClick = onTransfers,
        icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
        modifier =
            Modifier
                .padding(horizontal = OpenCloudDimensions.SpacingSm)
                .browserDescription(R.string.browser_navigate_to_uploads),
    )
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.browser_deleted_files)) },
        selected = false,
        onClick = onDeletedFiles,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        modifier =
            Modifier
                .padding(horizontal = OpenCloudDimensions.SpacingSm)
                .browserDescription(R.string.browser_navigate_to_deleted_files),
    )
    Spacer(Modifier.weight(1f))
    HorizontalDivider()
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.browser_settings)) },
        selected = false,
        onClick = onSettings,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        modifier =
            Modifier
                .padding(OpenCloudDimensions.SpacingSm)
                .browserDescription(R.string.browser_open_settings),
    )
    NavigationDrawerItem(label = {
        Text(stringResource(R.string.browser_community_discussions))
    }, selected = false, onClick = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/orgs/opencloud-eu/discussions")),
        )
    }, icon = {
        Icon(
            Icons.Default.Forum,
            null,
        )
    }, modifier = Modifier.padding(horizontal = OpenCloudDimensions.SpacingSm))
    personalSpace?.let { space ->
        val used = space.quotaUsedBytes
        val total = space.quotaBytes?.takeIf { it > 0 }
        Column(Modifier.padding(OpenCloudDimensions.SpacingMd)) {
            Text(stringResource(R.string.browser_personal_storage), style = MaterialTheme.typography.labelLarge)
            Text(
                browserQuotaSummary(used, total),
            )
            if (used != null &&
                total != null
            ) {
                LinearProgressIndicator(
                    progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.browser_version, releaseVersion),
        modifier = Modifier.padding(OpenCloudDimensions.SpacingXl),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

private val ACTIVE_TRANSFER_STATES =
    setOf(TransferState.QUEUED.name, TransferState.RUNNING.name, TransferState.RETRY.name)
private val FAILED_TRANSFER_STATES = setOf(TransferState.CONFLICT.name, TransferState.FAILED.name)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
private fun BrowserBottomNavigation(
    selectedDestination: FileBrowserDestination,
    onPersonal: () -> Unit,
    onFavorites: () -> Unit,
    onSpaces: () -> Unit,
) = NavigationBar(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.personalNavigationCrown(MaterialTheme.colorScheme.surfaceContainer),
) {
    listOf(
        FileBrowserDestination.Favorites,
        FileBrowserDestination.Personal,
        FileBrowserDestination.Spaces,
    ).forEach { destination ->
        // The standard ripple is sized for the smaller Material indicator and would
        // briefly overlap Personal's custom indicator. Its selected state gives feedback.
        val ripple = if (destination == FileBrowserDestination.Personal) null else LocalRippleConfiguration.current
        CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
            NavigationBarItem(
                colors =
                    androidx.compose.material3.NavigationBarItemDefaults.colors(
                        indicatorColor =
                            if (destination ==
                                FileBrowserDestination.Personal
                            ) {
                                OpenCloudColor.Transparent
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                    ),
                selected = destination == selectedDestination,
                onClick =
                    when (destination) {
                        FileBrowserDestination.Favorites -> onFavorites
                        FileBrowserDestination.Spaces -> onSpaces
                        else -> onPersonal
                    },
                icon = {
                    if (destination == FileBrowserDestination.Personal) {
                        PersonalNavigationIcon(destination.icon, destination == selectedDestination)
                    } else {
                        Icon(destination.icon, null)
                    }
                },
                label = {
                    Text(
                        stringResource(destination.labelResource),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier =
                    Modifier.browserDescription(
                        R.string.browser_navigate_to,
                        stringResource(destination.labelResource),
                    ),
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun BrowserList(
    offlinePins: List<ResourceEntity>,
    resources: List<ResourceEntity>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    condensed: Boolean,
    contentPadding: PaddingValues,
    onOpen: (ResourceEntity) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowActions: (ResourceEntity) -> Unit,
    display: FileDisplayOptions = FileDisplayOptions(),
) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding =
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom =
                contentPadding.calculateBottomPadding() + OpenCloudDimensions.ContentBottomClearance,
        ),
) {
    items(resources, key = { it.selectionKey }) { resource ->
        BrowserListItem(
            keptOffline = isKeptOffline(resource, offlinePins),
            display = display,
            resource = resource,
            selected = resource.selectionKey in selectedIds,
            selectionMode = selectionMode,
            condensed = condensed,
            onOpen = { onOpen(resource) },
            onSelect = { onToggleSelection(resource.selectionKey) },
            onActions = { onShowActions(resource) },
        )
    }
    item { FolderSummary(resources) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun BrowserListItem(
    keptOffline: Boolean,
    resource: ResourceEntity,
    selected: Boolean,
    selectionMode: Boolean,
    condensed: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onActions: () -> Unit,
    display: FileDisplayOptions = FileDisplayOptions(),
) {
    if (condensed) {
        CompactBrowserListItem(keptOffline, resource, selected, selectionMode, onOpen, onSelect, onActions, display)
    } else {
        ListItem(
            headlineContent = { ResourceName(resource, display = display) },
            supportingContent = { ResourceMetadata(resource, keptOffline, display) },
            leadingContent = { BadgedResourceThumbnail(resource) },
            trailingContent = {
                if (selectionMode) {
                    SelectionCircle(selected = selected, resourceName = resource.name, onSelect = onSelect)
                } else {
                    IconButton(onClick = onActions) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.browser_actions_for, resource.name),
                        )
                    }
                }
            },
            colors =
                ListItemDefaults.colors(
                    containerColor =
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else OpenCloudColor.Transparent,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectionMode) onSelect() else onOpen() },
                        onLongClick = onSelect,
                    ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun CompactBrowserListItem(
    keptOffline: Boolean,
    resource: ResourceEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onActions: () -> Unit,
    display: FileDisplayOptions = FileDisplayOptions(),
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = OpenCloudDimensions.CondensedRowHeight)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else OpenCloudColor.Transparent,
                ).combinedClickable(
                    onClick = { if (selectionMode) onSelect() else onOpen() },
                    onLongClick = onSelect,
                ).padding(horizontal = OpenCloudDimensions.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedResourceThumbnail(resource, Modifier.size(OpenCloudDimensions.IconMedium))
        Column(Modifier.weight(1f)) {
            ResourceName(resource, display = display)
            FileMetadataText(resource, display = display)
        }
        LocalAvailabilityIcon(resource, keptOffline)
        if (selectionMode) {
            SelectionCircle(
                selected = selected,
                resourceName = resource.name,
                onSelect = onSelect,
                modifier = Modifier.size(OpenCloudDimensions.CondensedRowHeight),
            )
        } else {
            IconButton(onClick = onActions, modifier = Modifier.size(OpenCloudDimensions.CondensedRowHeight)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.browser_actions_for, resource.name),
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun BrowserGrid(
    offlinePins: List<ResourceEntity>,
    resources: List<ResourceEntity>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    contentPadding: PaddingValues,
    onOpen: (ResourceEntity) -> Unit,
    onToggleSelection: (String) -> Unit,
    onShowActions: (ResourceEntity) -> Unit,
    display: FileDisplayOptions = FileDisplayOptions(),
) = LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding =
        PaddingValues(
            start = OpenCloudDimensions.SpacingMd,
            top = contentPadding.calculateTopPadding() + OpenCloudDimensions.SpacingMd,
            end = OpenCloudDimensions.SpacingMd,
            bottom = contentPadding.calculateBottomPadding() + OpenCloudDimensions.ContentBottomClearance,
        ),
    horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
    verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
) {
    items(resources, key = { it.selectionKey }) { resource ->
        BrowserGridItem(
            keptOffline = isKeptOffline(resource, offlinePins),
            display = display,
            resource = resource,
            selected = resource.selectionKey in selectedIds,
            selectionMode = selectionMode,
            onOpen = { onOpen(resource) },
            onSelect = { onToggleSelection(resource.selectionKey) },
            onActions = { onShowActions(resource) },
        )
    }
    item(span = {
        androidx.compose.foundation.lazy.grid
            .GridItemSpan(maxLineSpan)
    }) { FolderSummary(resources) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
private fun BrowserGridItem(
    keptOffline: Boolean,
    resource: ResourceEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onActions: () -> Unit,
    display: FileDisplayOptions = FileDisplayOptions(),
) = Card(
    modifier =
        Modifier.aspectRatio(1f).combinedClickable(
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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = OpenCloudDimensions.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                displayFileName(resource, display),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selectionMode) {
                SelectionCircle(selected = selected, resourceName = resource.name, onSelect = onSelect)
            } else {
                IconButton(onClick = onActions) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.browser_actions_for, resource.name),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
            BadgedResourceThumbnail(
                resource,
                if (resource.isImagePreview()) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(
                        OpenCloudDimensions.TouchTarget,
                    )
                },
            )
        }
        Box(Modifier.padding(OpenCloudDimensions.SpacingSm)) { ResourceMetadata(resource, keptOffline, display) }
    }
}

@Composable
private fun ResourceName(
    resource: ResourceEntity,
    modifier: Modifier = Modifier,
    display: FileDisplayOptions = FileDisplayOptions(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(displayFileName(resource, display), modifier = Modifier.weight(1f, fill = false), maxLines = 1)
    }
}

@Composable
private fun BadgedResourceThumbnail(
    resource: ResourceEntity,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(OpenCloudDimensions.TouchTarget)) {
        ResourceThumbnail(resource, Modifier.matchParentSize())
        if (resource.isFavorite) {
            Icon(
                Icons.Default.Star,
                stringResource(R.string.browser_favorite),
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(OpenCloudDimensions.SpacingMd)
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SelectionCircle(
    selected: Boolean,
    resourceName: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onSelect, modifier = modifier) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription =
                stringResource(
                    if (selected) R.string.browser_deselect_item else R.string.browser_select_item,
                    resourceName,
                ),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ResourceIcon(
    kind: ResourceKind,
    modifier: Modifier = Modifier,
) {
    val icon =
        if (kind == ResourceKind.FOLDER) {
            Icons.Default.Folder
        } else {
            Icons.AutoMirrored.Filled.InsertDriveFile
        }
    val description =
        stringResource(
            if (kind ==
                ResourceKind.FOLDER
            ) {
                R.string.browser_folder
            } else {
                R.string.browser_file
            },
        )
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
private fun ResourceMetadata(
    resource: ResourceEntity,
    keptOffline: Boolean,
    display: FileDisplayOptions = FileDisplayOptions(),
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileMetadataText(resource, Modifier.weight(1f, fill = false), display)
        LocalAvailabilityIcon(resource, keptOffline)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) = Row(
    modifier =
        Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = OpenCloudDimensions.SpacingMd)
            .heightIn(min = OpenCloudDimensions.CompactMenuRowHeight),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
) {
    Icon(icon, contentDescription = null, tint = color)
    Text(label, color = color)
}

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
    onShare: (() -> Unit)? = null,
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onOpenWith: (() -> Unit)? = null,
    onOpenWebApp: (() -> Unit)? = null,
    onEditText: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onSend: (() -> Unit)? = null,
    onExport: ((Boolean) -> Unit)? = null,
    onRemoveLocalCopy: () -> Unit = {},
    keptOffline: Boolean = resource.offlinePinned,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        rememberScrollState(),
                    ).padding(bottom = OpenCloudDimensions.SpacingMd),
        ) {
            Text(
                resource.name,
                modifier = Modifier.padding(OpenCloudDimensions.SpacingMd),
                style = MaterialTheme.typography.titleMedium,
            )
            onOpenWith?.let {
                SheetAction(
                    stringResource(R.string.browser_open_with),
                    Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = it,
                )
            }
            onOpenWebApp?.let {
                SheetAction(stringResource(R.string.browser_open_in_web_app), Icons.Default.Language, onClick = it)
            }
            onEditText?.let {
                SheetAction(
                    stringResource(R.string.browser_edit_text),
                    Icons.Default.Edit,
                    onClick = it,
                )
            }
            onSend?.let {
                SheetAction(
                    stringResource(R.string.browser_send),
                    Icons.AutoMirrored.Filled.Send,
                    onClick = it,
                )
            }
            onShare?.let { SheetAction(stringResource(R.string.browser_share), Icons.Outlined.People, onClick = it) }
            onDetails?.let { SheetAction(stringResource(R.string.browser_details), Icons.Default.Info, onClick = it) }
            ActionGroupDivider()
            SheetAction(stringResource(R.string.browser_rename), Icons.Default.Edit, onClick = onRename)
            SheetAction(
                stringResource(R.string.browser_move_to),
                Icons.AutoMirrored.Filled.DriveFileMove,
                onClick = onMove,
            )
            SheetAction(stringResource(R.string.browser_copy_to), Icons.Default.ContentCopy, onClick = onCopy)
            SheetAction(
                if (resource.isFavorite) {
                    stringResource(
                        R.string.browser_remove_from_favorites,
                    )
                } else {
                    stringResource(R.string.browser_add_to_favorites)
                },
                Icons.Default.Star,
                onClick = onToggleFavorite,
            )
            ActionGroupDivider()
            if (!keptOffline) {
                SheetAction(
                    stringResource(R.string.browser_make_available_offline),
                    Icons.Default.OfflinePin,
                    onClick = onDownloadForOffline,
                )
            }
            if (resource.hasLocalCopy || keptOffline) {
                SheetAction(
                    if (resource.kind ==
                        ResourceKind.FOLDER
                    ) {
                        stringResource(R.string.browser_stop_keeping_offline)
                    } else {
                        stringResource(R.string.browser_delete_local_copy)
                    },
                    cleanupIcon(),
                    onClick = onRemoveLocalCopy,
                )
            }
            onExport?.let { export ->
                SheetAction(
                    stringResource(R.string.browser_copy_to_device),
                    Icons.Default.ContentCopy,
                    onClick = { export(false) },
                )
            }
            ActionGroupDivider()
            SheetAction(
                stringResource(R.string.browser_delete),
                Icons.Default.Delete,
                MaterialTheme.colorScheme.error,
                onDelete,
            )
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
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_ok)) } },
)

@Composable
private fun ConflictResolutionDialog(
    conflict: TransferEntity,
    onDecision: (ConflictDecision) -> Unit,
) = AlertDialog(
    onDismissRequest = {},
    title = { Text(stringResource(R.string.browser_upload_conflict)) },
    text = { Text(stringResource(R.string.browser_conflict_message, conflict.displayName)) },
    confirmButton = {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
            ) {
                FilledTonalButton(
                    onClick = { onDecision(ConflictDecision.KEEP_BOTH) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.browser_keep_both), textAlign = TextAlign.Center) }
                FilledTonalButton(
                    onClick = { onDecision(ConflictDecision.REPLACE) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.browser_replace_remote), textAlign = TextAlign.Center) }
            }
            TextButton(onClick = { onDecision(ConflictDecision.CANCEL) }) {
                Text(stringResource(R.string.browser_cancel_upload))
            }
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
@Suppress("LongParameterList", "CyclomaticComplexMethod")
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
    initialBackup: FolderBackupEntity? = null,
    transfers: List<TransferEntity> = emptyList(),
) {
    var destination by rememberSaveable(initialBackup?.id) {
        mutableStateOf(
            initialBackup?.destinationPath ?: "/Camera Uploads",
        )
    }
    var showDestinationPicker by rememberSaveable { mutableStateOf(false) }
    var showCreateFolder by rememberSaveable { mutableStateOf(false) }
    var mediaType by rememberSaveable(initialBackup?.id) { mutableStateOf(initialBackup?.mediaType ?: "IMAGE") }
    var wifiOnly by rememberSaveable(initialBackup?.id) { mutableStateOf(initialBackup?.wifiOnly ?: true) }
    var chargingOnly by rememberSaveable(initialBackup?.id) { mutableStateOf(initialBackup?.chargingOnly ?: false) }
    var datedFolders by rememberSaveable(initialBackup?.id) {
        mutableStateOf(
            initialBackup?.dateOrganization == "YEAR_MONTH",
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(OpenCloudDimensions.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs),
    ) {
        BackupEditorHeading(initialBackup, transfers)
        Text(stringResource(R.string.browser_remote_destination), style = MaterialTheme.typography.labelLarge)
        Text(destination, style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = {
                onOpenPicker()
                showDestinationPicker = true
            },
        ) { Text(stringResource(R.string.browser_select_folder)) }
        Text(stringResource(R.string.browser_sort_file_type), style = MaterialTheme.typography.labelLarge)
        BackupMediaTypeOptions(mediaType) { mediaType = it }
        BackupCheckbox(stringResource(R.string.browser_wifi_only), wifiOnly) { wifiOnly = it }
        BackupCheckbox(stringResource(R.string.browser_charging_only), chargingOnly) { chargingOnly = it }
        BackupCheckbox(stringResource(R.string.browser_organize_by_year_month), datedFolders) { datedFolders = it }
        if (datedFolders) {
            Text(
                stringResource(R.string.browser_backup_date_organization),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(stringResource(R.string.browser_original_files_stay_local), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        backups.forEach { backup -> BackupConfigurationItem(backup = backup, onDelete = { onDelete(backup.id) }) }
        initialBackup?.let { backup ->
            TextButton(onClick = {
                onDelete(backup.id)
                onDismiss()
            }) { Text(stringResource(R.string.browser_remove_backup), color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_cancel)) }
            TextButton(
                onClick = {
                    onAdd(
                        BackupDraft(
                            destination,
                            mediaType,
                            wifiOnly,
                            chargingOnly,
                            false,
                            if (datedFolders) "YEAR_MONTH" else "NONE",
                        ),
                    )
                },
            ) {
                Text(
                    if (initialBackup ==
                        null
                    ) {
                        stringResource(R.string.browser_choose_source_folder)
                    } else {
                        stringResource(R.string.browser_save)
                    },
                )
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
            title = stringResource(R.string.browser_new_destination_folder),
            confirm = stringResource(R.string.browser_create),
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
            label = stringResource(R.string.browser_local_path_label),
            value = backup.sourceDisplayName.ifBlank { sourceNameFromTreeUri(backup.sourceTreeUri) },
        )
    },
    supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs)) {
            BackupPathLine(label = stringResource(R.string.browser_remote_path_label), value = backup.destinationPath)
            Text(
                backupDetails(backup, LocalContext.current),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    },
    trailingContent = {
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.browser_remove_backup)) }
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

@Composable
private fun BackupMediaTypeOptions(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row {
        listOf(
            "IMAGE" to R.string.browser_photo_type,
            "VIDEO" to R.string.browser_video_type,
            "ALL" to R.string.browser_all_files_type,
        ).forEach { (value, labelId) ->
            val label = stringResource(labelId)
            TextButton(onClick = { onSelect(value) }) {
                Text(
                    if (selected == value) {
                        stringResource(R.string.browser_selected_type, label)
                    } else {
                        label
                    },
                )
            }
        }
    }
}

/** Production callers supply Context; pure callers retain the established English fallback. */
internal fun backupDetails(
    backup: FolderBackupEntity,
    context: android.content.Context? = null,
): String {
    fun label(
        id: Int,
        fallback: String,
    ): String = context?.getString(id) ?: fallback
    val fileType =
        when (backup.mediaType) {
            "IMAGE" -> label(R.string.browser_photo_type, "Photos")
            "VIDEO" -> label(R.string.browser_video_type, "Videos")
            else -> label(R.string.browser_all_files_type, "All files")
        }
    val constraints =
        buildList {
            if (backup.wifiOnly) add(label(R.string.browser_wifi_only, "Wi-Fi only"))
            if (backup.chargingOnly) add(label(R.string.browser_charging, "Charging"))
        }.ifEmpty { listOf(label(R.string.browser_no_restrictions, "No restrictions")) }
    val separator = " ${label(R.string.browser_backup_separator, "•")} "
    return context?.getString(R.string.browser_backup_constraints, fileType, constraints.joinToString(separator))
        ?: "$fileType • ${constraints.joinToString(separator)}"
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
    title = { Text(stringResource(R.string.browser_select_remote_folder)) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXs)) {
            Text(
                trail.lastOrNull()?.path ?: "/",
                style = MaterialTheme.typography.bodySmall,
            )
            if (trail.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.browser_up)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onNavigateUp, onLongClick = {}),
                )
            }
            if (folders.isEmpty()) {
                Text(stringResource(R.string.browser_no_folders_here), style = MaterialTheme.typography.bodyMedium)
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
                Text(stringResource(R.string.browser_new_folder))
            }
        }
    },
    confirmButton = { TextButton(onClick = onSelect) { Text(stringResource(R.string.browser_select_this_folder)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_cancel)) } },
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
                label = { Text(stringResource(R.string.browser_sort_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_cancel)) } },
    )
}

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
    val dateOrganization: String = "NONE",
)

internal enum class BrowserSortCriterion(
    @androidx.annotation.StringRes val labelResource: Int,
) {
    Name(R.string.browser_sort_name),
    DateModified(R.string.browser_sort_date_modified),
    DateOpened(R.string.browser_sort_date_created),
    Size(R.string.browser_sort_size),
    Type(R.string.browser_sort_file_type),
    ;

    fun comparator(ascending: Boolean): Comparator<ResourceEntity> {
        val comparator =
            when (this) {
                Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { resource: ResourceEntity -> resource.name }
                DateModified -> compareBy<ResourceEntity> { it.modifiedAtEpochMillis }
                DateOpened -> compareBy<ResourceEntity> { it.createdAtEpochMillis }
                Size -> compareBy<ResourceEntity> { it.sizeBytes }
                Type ->
                    compareBy(String.CASE_INSENSITIVE_ORDER) { resource: ResourceEntity ->
                        resource.name.substringAfterLast('.', "").ifBlank { resource.mimeType.orEmpty() }
                    }
            }
        val ordered = comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        return compareBy<ResourceEntity> { it.kind != ResourceKind.FOLDER }
            .then(if (ascending) ordered else ordered.reversed())
    }
}

private fun BrowserLayout.next(): BrowserLayout =
    when (this) {
        BrowserLayout.DEFAULT_TABLE -> BrowserLayout.CONDENSED_TABLE
        BrowserLayout.CONDENSED_TABLE -> BrowserLayout.TILES
        BrowserLayout.TILES -> BrowserLayout.DEFAULT_TABLE
    }

private fun BrowserLayout.nextIcon(): ImageVector =
    when (this) {
        BrowserLayout.DEFAULT_TABLE -> Icons.Default.ViewHeadline
        BrowserLayout.CONDENSED_TABLE -> Icons.Default.GridView
        BrowserLayout.TILES -> Icons.AutoMirrored.Filled.FormatListBulleted
    }

@Composable
private fun BrowserLayout.nextContentDescription(): String =
    when (this) {
        BrowserLayout.DEFAULT_TABLE -> stringResource(R.string.browser_accessibility_layout_compact)
        BrowserLayout.CONDENSED_TABLE -> stringResource(R.string.browser_accessibility_layout_grid)
        BrowserLayout.TILES -> stringResource(R.string.browser_accessibility_layout_regular)
    }

enum class FileBrowserDestination(
    @androidx.annotation.StringRes val labelResource: Int,
    val icon: ImageVector,
) {
    Personal(R.string.browser_personal, Icons.Default.Folder),
    Favorites(R.string.browser_favorites, Icons.Default.Star),
    Shares(R.string.browser_shares, Icons.Default.Share),
    Spaces(R.string.browser_spaces, Icons.Default.Apps),
    Offline(R.string.browser_offline, Icons.Default.Smartphone),
    Recents(R.string.browser_recents, Icons.Default.History),
}
