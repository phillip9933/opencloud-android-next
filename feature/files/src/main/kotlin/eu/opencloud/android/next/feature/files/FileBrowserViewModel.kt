package eu.opencloud.android.next.feature.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.sync.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val transfers = TransferManager(application, store)
    private val mutableState = MutableStateFlow(FileBrowserUiState())
    private val activeLocation = MutableStateFlow<BrowserLocation?>(null)
    val state: StateFlow<FileBrowserUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        transfers.scheduleCleanup()
        viewModelScope.launch(Dispatchers.IO) {
            activeLocation
                .filterNotNull()
                .flatMapLatest { location ->
                    store.observeChildren(location.accountId, location.spaceId, location.folderId)
                }.collectLatest { resources ->
                    reduce { copy(resources = resources) }
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
            launch { transfers.reconcile() }
            runCatching { store.ensureSeeded(accountId) }
                .onFailure { reduce { copy(error = it.message ?: "Unable to open local files.") } }
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
    }

    fun navigateUp() {
        val trail = state.value.folderTrail
        if (trail.isEmpty()) return
        val nextTrail = trail.dropLast(1)
        reduce { copy(currentFolderId = nextTrail.lastOrNull()?.id, folderTrail = nextTrail, selectedIds = emptySet()) }
        state.value.spaceId?.let { spaceId -> setActiveLocation(spaceId, nextTrail.lastOrNull()?.id) }
    }

    fun setLayout(layout: BrowserLayout) = reduce { copy(layout = layout) }

    fun toggleSelection(resourceId: String) {
        val next = state.value.selectedIds.toMutableSet()
        if (!next.add(resourceId)) next.remove(resourceId)
        reduce { copy(selectedIds = next) }
    }

    fun clearSelection() = reduce { copy(selectedIds = emptySet()) }

    fun showActions(resource: ResourceEntity?) = reduce { copy(actionResource = resource) }

    fun dismissActions() = reduce { copy(actionResource = null) }

    fun createFolder(name: String) =
        mutate { account, space, parent -> store.createFolder(account, space, parent, name) }

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

    fun delete(resource: ResourceEntity) =
        mutate { account, space, _ -> store.delete(account, space, resource.remoteId) }

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

    fun download(resource: ResourceEntity) = enqueueDownload(resource, offlinePin = false)

    fun makeAvailableOffline(resource: ResourceEntity) = enqueueDownload(resource, offlinePin = true)

    private fun enqueueDownload(
        resource: ResourceEntity,
        offlinePin: Boolean,
    ) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { transfers.enqueueDownload(resource, offlinePin) } }
                .onSuccess {
                    reduce {
                        copy(
                            actionResource = null,
                            message = if (offlinePin) "Available-offline download queued." else "Download queued.",
                        )
                    }
                }.onFailure { reduce { copy(error = it.message ?: "The download could not be queued.") } }
        }
    }

    fun showGlobalActionUnavailable() =
        reduce { copy(message = "This global navigation action is not available in the file browser preview yet.") }

    fun clearMessage() = reduce { copy(message = null, error = null) }

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

    private fun reduce(transform: FileBrowserUiState.() -> FileBrowserUiState) {
        mutableState.value = mutableState.value.transform()
    }
}

data class FileBrowserUiState(
    val spaces: List<SpaceEntity> = emptyList(),
    val spaceId: String? = null,
    val currentFolderId: String? = null,
    val folderTrail: List<FolderCrumb> = emptyList(),
    val resources: List<ResourceEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val layout: BrowserLayout = BrowserLayout.DEFAULT_TABLE,
    val selectedIds: Set<String> = emptySet(),
    val actionResource: ResourceEntity? = null,
    val message: String? = null,
    val error: String? = null,
)

data class FolderCrumb(
    val id: String,
    val name: String,
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
