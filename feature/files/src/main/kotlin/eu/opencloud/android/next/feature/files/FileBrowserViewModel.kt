package eu.opencloud.android.next.feature.files

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.datastore.SettingsBrowserLayout
import eu.opencloud.android.next.core.datastore.SettingsRepository
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.sync.DISCOVERY_ERROR
import eu.opencloud.android.next.core.sync.SearchRepositoryResult
import eu.opencloud.android.next.core.sync.TransferManager
import eu.opencloud.android.next.core.sync.createSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val transfers = TransferManager(application, store)
    private val searchRepository = createSearchRepository(application, store)
    private val workManager = WorkManager.getInstance(application)
    private val settings = SettingsRepository.create(application)
    private val mutableState = MutableStateFlow(FileBrowserUiState())
    private val activeLocation = MutableStateFlow<BrowserLocation?>(null)
    private val backupPickerLocation = MutableStateFlow<BrowserLocation?>(null)
    private val searchQuery = MutableStateFlow("")
    val state: StateFlow<FileBrowserUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        transfers.scheduleCleanup()
        viewModelScope.launch(Dispatchers.IO) {
            settings.settings.collectLatest { settings ->
                reduce { copy(layout = settings.browserLayout.toBrowserLayout()) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            activeLocation
                .filterNotNull()
                .flatMapLatest { location ->
                    store.observeChildren(location.accountId, location.spaceId, location.folderId)
                }.collectLatest { resources ->
                    reduce { copy(resources = resources) }
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            searchQuery
                .flatMapLatest { query ->
                    val account = accountId
                    if (account == null || query.isBlank()) {
                        flowOf(SearchRepositoryResult(emptyList(), remoteSupported = false))
                    } else {
                        searchRepository.search(account, query)
                    }
                }.onStart { emit(SearchRepositoryResult(emptyList(), remoteSupported = false)) }
                .collectLatest { result ->
                    reduce {
                        copy(
                            searchResults = result.resources,
                            remoteSearchSupported = result.remoteSupported,
                            isRemoteSearchLoading = result.remoteLoading,
                            remoteSearchError = result.remoteError,
                        )
                    }
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            backupPickerLocation
                .filterNotNull()
                .flatMapLatest { location ->
                    store.observeChildren(location.accountId, location.spaceId, location.folderId)
                }.collectLatest { resources ->
                    reduce { copy(backupPickerResources = resources.filter { it.kind == ResourceKind.FOLDER }) }
                }
        }
    }

    fun load(accountId: String) {
        if (this.accountId == accountId) return
        this.accountId = accountId
        viewModelScope.launch(Dispatchers.IO) {
            launch {
                store.observeTransfers(accountId).collectLatest { transfers ->
                    reduce { copy(transfers = transfers) }
                }
            }
            launch {
                store.observeBackups(accountId).collectLatest { backups ->
                    reduce { copy(backups = backups) }
                }
            }
            launch { transfers.reconcile() }
            observeDiscovery(transfers.refreshAccount(accountId))
            store.observeSpaces(accountId).collectLatest { spaces ->
                val selectedSpace =
                    mutableState.value.spaceId?.let { selectedId -> spaces.find { it.driveId == selectedId } }
                        ?: spaces.firstOrNull()
                reduce {
                    copy(
                        spaces = spaces,
                        spaceId = spaceId ?: selectedSpace?.driveId,
                    )
                }
                if (activeLocation.value == null && selectedSpace != null) {
                    setActiveLocation(selectedSpace.driveId, null)
                }
            }
        }
    }

    fun selectSpace(spaceId: String) {
        reduce { copy(spaceId = spaceId, currentFolderId = null, folderTrail = emptyList(), selectedIds = emptySet()) }
        setActiveLocation(spaceId, null)
        accountId?.let { observeDiscovery(transfers.refreshFolder(it, spaceId, null)) }
    }

    fun open(resource: ResourceEntity) {
        if (resource.kind != ResourceKind.FOLDER) return
        reduce {
            copy(
                currentFolderId = resource.remoteId,
                folderTrail =
                    folderTrail + FolderCrumb(resource.remoteId, resource.name),
                selectedIds = emptySet(),
            )
        }
        setActiveLocation(resource.spaceId, resource.remoteId)
        accountId?.let { observeDiscovery(transfers.refreshFolder(it, resource.spaceId, resource.remoteId)) }
    }

    fun navigateUp() {
        val trail = state.value.folderTrail
        if (trail.isEmpty()) return
        val nextTrail = trail.dropLast(1)
        reduce { copy(currentFolderId = nextTrail.lastOrNull()?.id, folderTrail = nextTrail, selectedIds = emptySet()) }
        state.value.spaceId?.let { spaceId -> setActiveLocation(spaceId, nextTrail.lastOrNull()?.id) }
        state.value.spaceId?.let { spaceId ->
            accountId?.let { observeDiscovery(transfers.refreshFolder(it, spaceId, nextTrail.lastOrNull()?.id)) }
        }
    }

    fun setLayout(layout: BrowserLayout) {
        reduce { copy(layout = layout) }
        viewModelScope.launch(Dispatchers.IO) { settings.setBrowserLayout(layout.toSettingsLayout()) }
    }

    fun toggleSelection(resourceId: String) {
        val next = state.value.selectedIds.toMutableSet()
        if (!next.add(resourceId)) next.remove(resourceId)
        reduce { copy(selectedIds = next) }
    }

    fun clearSelection() = reduce { copy(selectedIds = emptySet()) }

    fun downloadSelection() =
        batchAction("Selected resources queued for offline access.") { resource ->
            transfers.makeAvailableOffline(resource)
        }

    fun deleteSelected() =
        batchAction("Selected resources deleted.") { resource ->
            transfers.delete(resource)
        }

    fun showActions(resource: ResourceEntity?) = reduce { copy(actionResource = resource) }

    fun dismissActions() = reduce { copy(actionResource = null) }

    fun createFolder(name: String) =
        mutate { account, space, parent -> transfers.createFolder(account, space, parent, name) }

    fun createSpace(name: String) {
        val account = accountId ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.createSpace(account, name) } }
                .onFailure { reduce { copy(error = it.message ?: "The space could not be created.") } }
        }
    }

    fun rename(
        resource: ResourceEntity,
        name: String,
    ) = mutate { account, space, _ -> store.rename(account, space, resource.remoteId, name) }

    fun move(resource: ResourceEntity) =
        mutate { account, space, parent -> store.move(account, space, resource.remoteId, parent) }

    fun copy(resource: ResourceEntity) =
        mutate { account, space, parent -> store.copy(account, space, resource.remoteId, parent) }

    fun delete(resource: ResourceEntity) = mutate { _, _, _ -> transfers.delete(resource) }

    fun upload(uri: Uri) {
        val account = accountId ?: return
        val space = state.value.spaceId ?: return
        val parentPath =
            state.value.folderTrail
                .joinToString(separator = "/", prefix = "/") { it.name }
                .takeIf { state.value.folderTrail.isNotEmpty() }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.enqueueUpload(account, space, parentPath, uri) } }
                .onSuccess { reduce { copy(message = "Upload queued.") } }
                .onFailure { reduce { copy(error = it.message ?: "The upload could not be queued.") } }
        }
    }

    fun downloadForOffline(resource: ResourceEntity) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.makeAvailableOffline(resource) } }
                .onSuccess { reduce { copy(actionResource = null, message = "Offline synchronization queued.") } }
                .onFailure { reduce { copy(error = it.message ?: "Offline synchronization could not be queued.") } }
        }
    }

    fun toggleFavorite(resource: ResourceEntity) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.setFavorite(resource, !resource.isFavorite) } }
                .onSuccess {
                    reduce {
                        copy(
                            actionResource = null,
                            message = if (resource.isFavorite) "Removed from favorites." else "Added to favorites.",
                        )
                    }
                }.onFailure { reduce { copy(error = it.message ?: "Favorite could not be updated.") } }
        }
    }

    fun setSearchQuery(query: String) {
        reduce { copy(searchQuery = query, remoteSearchError = null) }
        searchQuery.value = query
    }

    @Suppress("LongParameterList")
    fun saveBackup(
        sourceTreeUri: Uri,
        destinationPath: String,
        mediaType: String,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        deleteAfterUpload: Boolean,
    ) {
        val account = accountId ?: return
        val space = state.value.spaceId ?: return
        viewModelScope.launch {
            val backup =
                FolderBackupEntity(
                    UUID.randomUUID().toString(),
                    account,
                    space,
                    sourceTreeUri.toString(),
                    sourceDirectoryName(sourceTreeUri),
                    destinationPath
                        .ifBlank {
                            "/Camera Uploads"
                        },
                    mediaType,
                    wifiOnly,
                    chargingOnly,
                    deleteAfterUpload,
                )
            runCatching { withContext(Dispatchers.IO) { transfers.saveBackup(backup) } }
                .onSuccess { reduce { copy(message = "Folder backup configured.") } }
                .onFailure { reduce { copy(error = it.message ?: "The folder backup could not be saved.") } }
        }
    }

    fun deleteBackup(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.deleteBackup(id) }
    }

    fun openBackupPicker() {
        val account = accountId ?: return
        val space = state.value.spaceId ?: return
        reduce { copy(backupPickerTrail = emptyList(), backupPickerResources = emptyList()) }
        backupPickerLocation.value = BrowserLocation(account, space, null)
        observeDiscovery(transfers.refreshFolder(account, space, null))
    }

    fun openBackupPickerFolder(folder: ResourceEntity) {
        if (folder.kind != ResourceKind.FOLDER) return
        val account = accountId ?: return
        reduce {
            copy(
                backupPickerTrail = backupPickerTrail + BackupFolderCrumb(folder.remoteId, folder.name, folder.path),
                backupPickerResources = emptyList(),
            )
        }
        backupPickerLocation.value = BrowserLocation(account, folder.spaceId, folder.remoteId)
        observeDiscovery(transfers.refreshFolder(account, folder.spaceId, folder.remoteId))
    }

    fun navigateBackupPickerUp() {
        val account = accountId ?: return
        val space = state.value.spaceId ?: return
        val nextTrail = state.value.backupPickerTrail.dropLast(1)
        reduce { copy(backupPickerTrail = nextTrail, backupPickerResources = emptyList()) }
        backupPickerLocation.value = BrowserLocation(account, space, nextTrail.lastOrNull()?.id)
        observeDiscovery(transfers.refreshFolder(account, space, nextTrail.lastOrNull()?.id))
    }

    fun createBackupPickerFolder(name: String) {
        val account = accountId ?: return
        val space = state.value.spaceId ?: return
        val parentId =
            state.value.backupPickerTrail
                .lastOrNull()
                ?.id
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.createFolder(account, space, parentId, name) } }
                .onFailure { reduce { copy(error = it.message ?: "The destination folder could not be created.") } }
        }
    }

    fun resolveConflict(
        transfer: TransferEntity,
        decision: ConflictDecision,
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (decision) {
                        ConflictDecision.REPLACE -> transfers.retryConflict(transfer, overwrite = true)
                        ConflictDecision.KEEP_BOTH ->
                            transfers.retryConflict(
                                transfer,
                                overwrite = false,
                                keepBoth = true,
                            )
                        ConflictDecision.CANCEL -> transfers.cancelConflict(transfer)
                    }
                }
            }.onFailure { reduce { copy(error = it.message ?: "The conflict decision could not be applied.") } }
        }
    }

    fun showGlobalActionUnavailable() =
        reduce { copy(message = "This global navigation action is not available in the file browser preview yet.") }

    fun clearMessage() = reduce { copy(message = null, error = null) }

    private fun observeDiscovery(workId: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collectLatest { workInfo ->
                if (workInfo?.state == WorkInfo.State.FAILED) {
                    val message = workInfo.outputData.getString(DISCOVERY_ERROR)
                    reduce { copy(error = message ?: "Remote discovery failed.") }
                }
            }
        }
    }

    private fun setActiveLocation(
        spaceId: String,
        folderId: String?,
    ) {
        accountId?.let { accountId -> activeLocation.value = BrowserLocation(accountId, spaceId, folderId) }
    }

    private fun mutate(action: suspend (String, String, String?) -> Unit) {
        val account = accountId ?: return
        val space = state.value.spaceId ?: return
        val parent = state.value.currentFolderId
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action(account, space, parent) } }
                .onSuccess { reduce { copy(selectedIds = emptySet(), actionResource = null) } }
                .onFailure {
                    reduce {
                        copy(
                            error = it.message ?: "That local file operation could not be completed.",
                        )
                    }
                }
        }
    }

    private fun selectedResources(): List<ResourceEntity> =
        state.value.resources.filter { it.remoteId in state.value.selectedIds }

    private fun batchAction(
        successMessage: String,
        action: suspend (ResourceEntity) -> Unit,
    ) {
        val selected = selectedResources()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { selected.forEach { action(it) } } }
                .onSuccess { reduce { copy(selectedIds = emptySet(), message = successMessage) } }
                .onFailure { reduce { copy(error = it.message ?: "The selected action could not be completed.") } }
        }
    }

    private fun reduce(transform: FileBrowserUiState.() -> FileBrowserUiState) {
        mutableState.value = mutableState.value.transform()
    }

    private fun sourceDirectoryName(treeUri: Uri): String {
        val displayName =
            runCatching {
                val documentUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri),
                    )
                getApplication<Application>()
                    .contentResolver
                    .query(
                        documentUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
        return displayName?.takeIf(String::isNotBlank) ?: sourceNameFromTreeUri(treeUri.toString())
    }
}

internal fun sourceNameFromTreeUri(sourceTreeUri: String): String =
    Uri
        .decode(Uri.parse(sourceTreeUri).lastPathSegment.orEmpty())
        .substringAfterLast(':')
        .substringAfterLast('/')
        .ifBlank { "Folder" }

data class FileBrowserUiState(
    val spaces: List<SpaceEntity> = emptyList(),
    val spaceId: String? = null,
    val currentFolderId: String? = null,
    val folderTrail: List<FolderCrumb> = emptyList(),
    val resources: List<ResourceEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val backups: List<FolderBackupEntity> = emptyList(),
    val backupPickerTrail: List<BackupFolderCrumb> = emptyList(),
    val backupPickerResources: List<ResourceEntity> = emptyList(),
    val layout: BrowserLayout = BrowserLayout.DEFAULT_TABLE,
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<ResourceEntity> = emptyList(),
    val remoteSearchSupported: Boolean = false,
    val isRemoteSearchLoading: Boolean = false,
    val remoteSearchError: String? = null,
    val actionResource: ResourceEntity? = null,
    val message: String? = null,
    val error: String? = null,
)

data class FolderCrumb(
    val id: String,
    val name: String,
)

data class BackupFolderCrumb(
    val id: String,
    val name: String,
    val path: String,
)

private data class BrowserLocation(
    val accountId: String,
    val spaceId: String,
    val folderId: String?,
)

enum class BrowserLayout {
    DEFAULT_TABLE,
    CONDENSED_TABLE,
    TILES,
}

enum class ConflictDecision { REPLACE, KEEP_BOTH, CANCEL }

private fun SettingsBrowserLayout.toBrowserLayout() =
    when (this) {
        SettingsBrowserLayout.DEFAULT_TABLE -> BrowserLayout.DEFAULT_TABLE
        SettingsBrowserLayout.CONDENSED_TABLE -> BrowserLayout.CONDENSED_TABLE
        SettingsBrowserLayout.TILES -> BrowserLayout.TILES
    }

private fun BrowserLayout.toSettingsLayout() =
    when (this) {
        BrowserLayout.DEFAULT_TABLE -> SettingsBrowserLayout.DEFAULT_TABLE
        BrowserLayout.CONDENSED_TABLE -> SettingsBrowserLayout.CONDENSED_TABLE
        BrowserLayout.TILES -> SettingsBrowserLayout.TILES
    }
