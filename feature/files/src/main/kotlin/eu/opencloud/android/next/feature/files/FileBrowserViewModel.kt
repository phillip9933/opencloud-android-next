package eu.opencloud.android.next.feature.files

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.datastore.FileDisplayOptions
import eu.opencloud.android.next.core.datastore.SettingsBrowserLayout
import eu.opencloud.android.next.core.datastore.SettingsRepository
import eu.opencloud.android.next.core.designsystem.localizedString
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.network.safeMessage
import eu.opencloud.android.next.core.network.toOpenCloudError
import eu.opencloud.android.next.core.sync.DISCOVERY_ERROR
import eu.opencloud.android.next.core.sync.SearchRepositoryResult
import eu.opencloud.android.next.core.sync.SpaceCreationResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val restoredAccount: String? = savedState["browser.account"]
    private val restoredSpace: String? = savedState["browser.space"]
    private val restoredFolder: String? = savedState["browser.folder"]
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val transfers = TransferManager(application, store)
    private val operations =
        eu.opencloud.android.next.core.sync
            .FileOperationManager(application)
    private val searchRepository = createSearchRepository(application, store)
    private val workManager = WorkManager.getInstance(application)
    private val settings = SettingsRepository.create(application)
    private val recentFiles =
        eu.opencloud.android.next.core.datastore
            .RecentFiles(application)
    private val mutableState = MutableStateFlow(FileBrowserUiState())
    private val activeLocation = MutableStateFlow<BrowserLocation?>(null)
    private val backupPickerLocation = MutableStateFlow<BrowserLocation?>(null)
    private val searchQuery = MutableStateFlow("")
    val state: StateFlow<FileBrowserUiState> = mutableState.asStateFlow()

    private var accountId: String? = null
    private var latestDiscovery: UUID? = null
    private var creatingSpace = false

    init {
        transfers.scheduleCleanup()
        viewModelScope.launch(Dispatchers.IO) {
            settings.settings.collectLatest { settings ->
                reduce {
                    copy(
                        layout = settings.browserLayout.toBrowserLayout(),
                        fileDisplay = settings.fileDisplay,
                        temporaryCopyRetentionHours = settings.temporaryCopyRetentionHours,
                    )
                }
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
                store.observeOffline(accountId).collectLatest { values ->
                    val bytes = downloadedBytes(getApplication<Application>(), values)
                    reduce { copy(offlineResources = values, offlineBytes = bytes) }
                }
            }
            launch {
                recentFiles
                    .observe(accountId)
                    .flatMapLatest { references ->
                        store.observeRecent(accountId, references.map { it.resourceId }).map { resources ->
                            references.mapNotNull { ref ->
                                resources.firstOrNull {
                                    it.remoteId == ref.resourceId &&
                                        it.spaceId == ref.spaceId
                                }
                            }
                        }
                    }.collectLatest { values -> reduce { copy(recentResources = values) } }
            }
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
            launch {
                store.observeOfflinePins(accountId).collectLatest { values ->
                    reduce { copy(offlinePins = values) }
                }
            }
            launch { transfers.reconcile() }
            launch { operations.reconcile() }
            launch { operations.observe(accountId).collectLatest { values -> reduce { copy(operations = values) } } }
            observeDiscovery(transfers.refreshAccount(accountId))
            store.observeSpaces(accountId).collectLatest { spaces ->
                val selectedSpace =
                    mutableState.value.spaceId
                        ?.let { selectedId -> spaces.find { it.driveId == selectedId } }
                        ?: preferredInitialBrowserSpace(spaces)
                reduce {
                    copy(
                        spaces = spaces,
                        spaceId = spaceId ?: selectedSpace?.driveId,
                    )
                }
                if (activeLocation.value == null && selectedSpace != null) {
                    restoreLocation(selectedSpace.driveId)
                }
            }
        }
    }

    fun selectSpace(spaceId: String) {
        reduce { copy(spaceId = spaceId, currentFolderId = null, folderTrail = emptyList(), selectedIds = emptySet()) }
        setActiveLocation(spaceId, null)
        accountId?.let { observeDiscovery(transfers.refreshFolder(it, spaceId, null)) }
    }

    fun recordOpened(resource: ResourceEntity) =
        recentFiles.record(resource.accountId, resource.spaceId, resource.remoteId)

    suspend fun prepareExternalFile(resource: ResourceEntity): ResourceEntity =
        eu.opencloud.android.next.core.sync
            .prepareLocalResource(getApplication(), resource)

    fun exportFile(
        spaceId: String,
        resourceId: String,
        destination: Uri,
        removeLocal: Boolean,
    ) {
        val account = accountId ?: return
        if (state.value.exporting) return
        reduce { copy(exporting = true, exportStatus = "Preparing export…") }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resource = requireNotNull(store.resource(account, spaceId, resourceId))
                val ready = prepareExternalFile(resource)
                reduce { copy(exportStatus = "Exporting ${ready.name}…") }
                eu.opencloud.android.next.core.sync
                    .exportCachedFile(getApplication(), ready, destination)
                if (removeLocal) transfers.removeLocalCopy(ready, requireSameCopy = true)
                reduce {
                    copy(
                        exportStatus =
                            if (removeLocal) {
                                "Exported; app's local copy removed. Cloud file kept."
                            } else {
                                "Saved to the selected location."
                            },
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                reduce {
                    copy(
                        exportStatus =
                            "Export did not finish. Your cloud file is unchanged. " +
                                "Check the destination before retrying.",
                    )
                }
            } finally {
                reduce { copy(exporting = false) }
            }
        }
    }

    fun dismissExportStatus() = reduce { copy(exportStatus = null) }

    fun refresh() {
        val location = activeLocation.value ?: return
        observeDiscovery(transfers.refreshFolder(location.accountId, location.spaceId, location.folderId))
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

    fun browseSharedResource(resource: ResourceEntity) {
        val account = accountId ?: return
        if (resource.accountId != account) return
        viewModelScope.launch(Dispatchers.IO) {
            val trail = mutableListOf<FolderCrumb>()
            val visited = mutableSetOf<String>()
            var pendingId = if (resource.kind == ResourceKind.FOLDER) resource.remoteId else resource.parentId
            var folder = pendingId?.let { store.resource(account, resource.spaceId, it) }
            while (folder?.kind == ResourceKind.FOLDER && visited.size < 256) {
                if (!visited.add(folder.remoteId)) break
                trail.add(FolderCrumb(folder.remoteId, folder.name))
                pendingId = folder.parentId
                folder = pendingId?.let { store.resource(account, resource.spaceId, it) }
            }
            if (pendingId != null) {
                reportOpenError("The folder location is incomplete. Refresh its Space and try again.")
                return@launch
            }
            val folderId = trail.firstOrNull()?.id
            reduce {
                copy(
                    spaceId = resource.spaceId,
                    currentFolderId = folderId,
                    folderTrail = trail.asReversed(),
                    selectedIds = emptySet(),
                    searchQuery = "",
                    searchResults = emptyList(),
                )
            }
            setActiveLocation(resource.spaceId, folderId)
            observeDiscovery(transfers.refreshFolder(account, resource.spaceId, folderId))
        }
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

    fun reportOpenError(message: String) = reduce { copy(error = message) }

    fun clearSelection() = reduce { copy(selectedIds = emptySet()) }

    fun downloadSelection() =
        batchAction { resource ->
            transfers.makeAvailableOffline(resource)
        }

    fun deleteSelected() =
        batchAction { resource ->
            transfers.delete(resource)
        }

    fun removeSelectedLocalCopies() =
        batchAction { resource ->
            if (resource.hasLocalCopy && resource.kind == ResourceKind.FILE) transfers.removeLocalCopy(resource)
        }

    fun showActions(resource: ResourceEntity?) = reduce { copy(actionResource = resource) }

    fun dismissActions() = reduce { copy(actionResource = null) }

    fun createFolder(name: String) =
        mutate { account, space, parent -> transfers.createFolder(account, space, parent, name) }

    fun createSpace(name: String) {
        val account = accountId ?: return
        if (creatingSpace) return
        creatingSpace = true
        viewModelScope.launch {
            try {
                runCatching { createProjectSpace(getApplication(), store, account, name) }
                    .onSuccess { result ->
                        if (accountId == account && result is SpaceCreationResult.AwaitingDiscovery) {
                            reduce { copy(message = result.message(getApplication())) }
                        }
                    }.onFailure {
                        val message = it.toOpenCloudError().safeMessage(getApplication())
                        if (accountId == account) reduce { copy(error = message) }
                    }
            } finally {
                creatingSpace = false
            }
        }
    }

    fun rename(
        resource: ResourceEntity,
        name: String,
    ) = mutate { _, _, _ -> operations.enqueue(resource, resource.spaceId, resource.parentId, name, true) }

    fun move(resource: ResourceEntity) =
        reduce {
            copy(clipboard = resource, clipboardItems = emptyList(), moving = true, actionResource = null)
        }

    fun copy(resource: ResourceEntity) =
        reduce {
            copy(clipboard = resource, clipboardItems = emptyList(), moving = false, actionResource = null)
        }

    fun moveSelection() = prepareSelection(true)

    fun copySelection() = prepareSelection(false)

    fun favoriteSelection() = batchAction { transfers.setFavorite(it, true) }

    private fun prepareSelection(move: Boolean) {
        val sources = selectedResources()
        reduce {
            copy(
                clipboard = sources.firstOrNull(),
                clipboardItems = sources,
                moving = move,
                selectedIds = emptySet(),
            )
        }
    }

    fun cancelPlacement() = reduce { copy(clipboard = null, clipboardItems = emptyList()) }

    fun place() =
        mutate { _, space, parent ->
            val sources = state.value.clipboardItems.ifEmpty { listOfNotNull(state.value.clipboard) }
            sources.forEach { source ->
                operations.enqueue(source, space, parent, source.name, state.value.moving)
                reduce {
                    val remaining =
                        clipboardItems.filterNot {
                            it.remoteId == source.remoteId &&
                                it.spaceId == source.spaceId
                        }
                    copy(clipboard = remaining.firstOrNull(), clipboardItems = remaining)
                }
            }
        }

    fun retryOperation(id: String) =
        mutate { account, _, _ ->
            operations.retry(id, account)
        }

    fun dismissOperation(id: String) = mutate { account, _, _ -> operations.dismiss(id, account) }

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
                .onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
        }
    }

    fun removeLocalCopy(resource: ResourceEntity) = mutate { _, _, _ -> transfers.removeLocalCopy(resource) }

    fun downloadForOffline(resource: ResourceEntity) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.makeAvailableOffline(resource) } }
                .onSuccess { reduce { copy(actionResource = null) } }
                .onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
        }
    }

    fun toggleFavorite(resource: ResourceEntity) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.setFavorite(resource, !resource.isFavorite) } }
                .onSuccess {
                    reduce {
                        copy(
                            actionResource = null,
                            message = null,
                        )
                    }
                }.onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
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
        dateOrganization: String = "NONE",
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
                    getApplication<Application>().sourceDirectoryName(sourceTreeUri),
                    destinationPath
                        .ifBlank {
                            "/Camera Uploads"
                        },
                    mediaType,
                    wifiOnly,
                    chargingOnly,
                    deleteAfterUpload,
                    dateOrganization = dateOrganization,
                )
            runCatching { withContext(Dispatchers.IO) { transfers.saveBackup(backup) } }
                .onSuccess {
                    reduce {
                        copy(
                            message =
                                getApplication<Application>().localizedString(R.string.browser_backup_configured),
                        )
                    }
                }.onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
        }
    }

    fun scanBackupsNow() = transfers.scanBackupsNow()

    fun deleteBackup(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.deleteBackup(id) }
    }

    fun updateBackup(
        backup: FolderBackupEntity,
        draft: BackupDraft,
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    transfers.saveBackup(
                        backup.copy(
                            destinationPath = draft.destinationPath,
                            mediaType = draft.mediaType,
                            wifiOnly = draft.wifiOnly,
                            chargingOnly = draft.chargingOnly,
                            deleteAfterUpload = false,
                            lastSafeScanEpochMillis = 0,
                            dateOrganization = draft.dateOrganization,
                        ),
                    )
                }
            }.onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
        }
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
                .onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
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
            }.onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
        }
    }

    fun showGlobalActionUnavailable() =
        reduce {
            copy(
                message =
                    getApplication<Application>().localizedString(R.string.browser_preview_navigation_unavailable),
            )
        }

    fun clearMessage() = reduce { copy(message = null, error = null) }

    private fun observeDiscovery(workId: UUID) {
        latestDiscovery = workId
        reduce { copy(discoveryError = null) }
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collectLatest { workInfo ->
                if (latestDiscovery == workId) reduce { copy(refreshing = workInfo?.state == WorkInfo.State.RUNNING) }
                if (latestDiscovery == workId && workInfo?.state == WorkInfo.State.FAILED) {
                    val message = workInfo.outputData.getString(DISCOVERY_ERROR)
                    reduce { copy(discoveryError = message ?: "Remote discovery failed.") }
                }
            }
        }
    }

    private fun setActiveLocation(
        spaceId: String,
        folderId: String?,
    ) {
        accountId?.let { accountId ->
            activeLocation.value = BrowserLocation(accountId, spaceId, folderId)
            viewModelScope.launch(Dispatchers.Main.immediate) {
                savedState["browser.account"] = accountId
                savedState["browser.space"] = spaceId
                savedState["browser.folder"] = folderId
            }
        }
    }

    private suspend fun restoreLocation(spaceId: String) {
        val account = requireNotNull(accountId)
        val folderId = restoredFolder.takeIf { restoredAccount == account && restoredSpace == spaceId }
        val trail = mutableListOf<FolderCrumb>()
        val visited = mutableSetOf<String>()
        var current = folderId?.let { store.resource(account, spaceId, it) }
        while (current?.kind == ResourceKind.FOLDER && visited.size < 256) {
            if (!visited.add(current.remoteId)) break
            trail.add(FolderCrumb(current.remoteId, current.name))
            current = current.parentId?.let { store.resource(account, spaceId, it) }
        }
        val restored = trail.asReversed().takeIf { current == null }.orEmpty()
        reduce { copy(currentFolderId = restored.lastOrNull()?.id, folderTrail = restored) }
        setActiveLocation(spaceId, restored.lastOrNull()?.id)
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
                            error = it.toOpenCloudError().safeMessage(getApplication()),
                        )
                    }
                }
        }
    }

    private fun selectedResources(): List<ResourceEntity> =
        (state.value.resources + state.value.searchResults + state.value.offlineResources)
            .distinctBy { it.selectionKey }
            .filter { it.selectionKey in state.value.selectedIds }

    @Suppress("TooGenericExceptionCaught") // Map failures at the UI boundary; the mapper rethrows cancellation.
    private fun batchAction(action: suspend (ResourceEntity) -> Unit) {
        val selected = selectedResources()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { selected.forEach { action(it) } } }
                .onSuccess { reduce { copy(selectedIds = emptySet()) } }
                .onFailure { reduce { copy(error = it.toOpenCloudError().safeMessage(getApplication())) } }
        }
    }

    private fun reduce(transform: FileBrowserUiState.() -> FileBrowserUiState) {
        mutableState.value = mutableState.value.transform()
    }
}

internal fun preferredInitialBrowserSpace(spaces: List<SpaceEntity>): SpaceEntity? =
    spaces.firstOrNull { it.type.equals("personal", ignoreCase = true) }

private fun Application.sourceDirectoryName(treeUri: Uri): String {
    val displayName =
        runCatching {
            val documentUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            contentResolver
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

internal fun sourceNameFromTreeUri(sourceTreeUri: String): String =
    Uri
        .decode(Uri.parse(sourceTreeUri).lastPathSegment.orEmpty())
        .substringAfterLast(':')
        .substringAfterLast('/')
        .ifBlank { "Folder" }

data class FileBrowserUiState(
    val fileDisplay: FileDisplayOptions = FileDisplayOptions(),
    val temporaryCopyRetentionHours: Int = 0,
    val exporting: Boolean = false,
    val exportStatus: String? = null,
    val clipboard: ResourceEntity? = null,
    val clipboardItems: List<ResourceEntity> = emptyList(),
    val moving: Boolean = false,
    val operations: List<eu.opencloud.android.next.core.database.FileOperationEntity> = emptyList(),
    val spaces: List<SpaceEntity> = emptyList(),
    val spaceId: String? = null,
    val currentFolderId: String? = null,
    val folderTrail: List<FolderCrumb> = emptyList(),
    val resources: List<ResourceEntity> = emptyList(),
    val offlineResources: List<ResourceEntity> = emptyList(),
    val offlinePins: List<ResourceEntity> = emptyList(),
    val offlineBytes: Long = 0,
    val recentResources: List<ResourceEntity> = emptyList(),
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
    val refreshing: Boolean = false,
    val discoveryError: String? = null,
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
