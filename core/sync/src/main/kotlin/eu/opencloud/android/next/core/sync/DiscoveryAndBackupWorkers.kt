package eu.opencloud.android.next.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.network.RemoteDiscoveryClient
import eu.opencloud.android.next.core.security.TlsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class AccountDiscoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = store()
                val accountId = requireNotNull(inputData.getString(ACCOUNT_ID))
                val account = requireNotNull(store.account(accountId))
                val remote = remote(account.serverUrl)
                val authorization = WorkerAuthorizationProvider(applicationContext).authorization(account)
                val spaces =
                    remote.spaces(account.serverUrl, authorization).map {
                        SpaceEntity(
                            account.id,
                            it.id,
                            it.name,
                            it.type,
                            it.description,
                            it.ownerId,
                            it.rootId,
                            it.rootWebDavUrl,
                            it.rootETag,
                            it.quotaBytes,
                            it.disabled,
                            it.deleted,
                        )
                    }
                store.replaceRemoteSpaces(account.id, spaces)
                spaces.filterNot { it.isDeleted || it.isDisabled }.forEach { space ->
                    refreshFolder(FolderRefresh(store, remote, account.id, space, null, "/", authorization))
                }
            }.fold(onSuccess = { Result.success() }, onFailure = { discoveryFailure("Account discovery", it) })
        }

    companion object {
        const val ACCOUNT_ID = "accountId"
    }
}

class FolderDiscoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = store()
                val accountId = requireNotNull(inputData.getString(ACCOUNT_ID))
                val spaceId = requireNotNull(inputData.getString(SPACE_ID))
                val folderId = inputData.getString(FOLDER_ID)
                val account = requireNotNull(store.account(accountId))
                val space = requireNotNull(store.space(accountId, spaceId))
                val path = folderId?.let { requireNotNull(store.resource(accountId, spaceId, it)).path } ?: "/"
                refreshFolder(
                    FolderRefresh(
                        store,
                        remote(account.serverUrl),
                        accountId,
                        space,
                        folderId,
                        path,
                        WorkerAuthorizationProvider(applicationContext).authorization(account),
                    ),
                )
            }.fold(onSuccess = { Result.success() }, onFailure = { discoveryFailure("Folder discovery", it) })
        }

    companion object {
        const val ACCOUNT_ID = "accountId"
        const val SPACE_ID = "spaceId"
        const val FOLDER_ID = "folderId"
    }
}

class OfflineSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = store()
                val manager = TransferManager(applicationContext, store)
                val roots =
                    inputData.getString(RESOURCE_ID)?.let { id ->
                        val accountId = requireNotNull(inputData.getString(ACCOUNT_ID))
                        val spaceId = requireNotNull(inputData.getString(SPACE_ID))
                        listOf(requireNotNull(store.resource(accountId, spaceId, id)))
                    } ?: store.offlinePinnedResources()
                roots.forEach { root -> syncOffline(root, store, manager) }
            }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
        }

    private suspend fun syncOffline(
        root: ResourceEntity,
        store: FileBrowserStore,
        manager: TransferManager,
    ) {
        store.setOfflinePinned(root, true)
        if (root.kind == ResourceKind.FILE) {
            manager.enqueueDownload(root, true)
            return
        }
        val account = requireNotNull(store.account(root.accountId))
        val space = requireNotNull(store.space(root.accountId, root.spaceId))
        val client = remote(account.serverUrl)
        val authorization = WorkerAuthorizationProvider(applicationContext).authorization(account)

        suspend fun recurse(folder: ResourceEntity) {
            refreshFolder(
                FolderRefresh(
                    store,
                    client,
                    folder.accountId,
                    space,
                    folder.remoteId,
                    folder.path,
                    authorization,
                ),
            )
            store.children(folder.accountId, folder.spaceId, folder.remoteId).forEach { child ->
                store.setOfflinePinned(child, true)
                if (child.kind == ResourceKind.FOLDER) recurse(child) else manager.enqueueDownload(child, true)
            }
        }
        recurse(root)
    }

    companion object {
        const val ACCOUNT_ID = "accountId"
        const val SPACE_ID = "spaceId"
        const val RESOURCE_ID = "resourceId"
    }
}

class FolderBackupScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = store()
                val manager = TransferManager(applicationContext, store)
                val now = System.currentTimeMillis()
                store.enabledBackups().forEach { backup -> scan(backup, now, manager, store) }
            }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
        }

    private suspend fun scan(
        backup: FolderBackupEntity,
        now: Long,
        manager: TransferManager,
        store: FileBrowserStore,
    ) {
        if (!BackupExecutionPolicy.canRun(
                debug = BuildConfig.DEBUG,
                wifiOnly = backup.wifiOnly,
                chargingOnly = backup.chargingOnly,
                unmetered = isUnmetered(),
                charging = isCharging(),
            )
        ) {
            return
        }
        val safeTime = now - WRITE_SAFETY_BUFFER_MS
        queryTree(Uri.parse(backup.sourceTreeUri))
            .filter { document ->
                document.modified in backup.lastSafeScanEpochMillis until safeTime &&
                    (backup.mediaType == "ALL" || document.mimeType.startsWith(backup.mediaType.lowercase() + "/"))
            }.forEach { document ->
                manager.enqueueUpload(
                    backup.accountId,
                    backup.spaceId,
                    backup.destinationPath,
                    document.uri,
                    backup.deleteAfterUpload,
                )
            }
        store.saveBackup(backup.copy(lastSafeScanEpochMillis = safeTime))
    }

    private fun queryTree(treeUri: Uri): List<LocalDocument> {
        val resolver = applicationContext.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val result = mutableListOf<LocalDocument>()

        fun visit(documentId: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                val rows = mutableListOf<Triple<String, String, Long>>()
                while (cursor.moveToNext()) {
                    rows +=
                        Triple(cursor.getString(0), cursor.getString(2).orEmpty(), cursor.getLong(3))
                }
                rows.forEach { (id, mime, modified) ->
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        visit(id)
                    } else {
                        result +=
                            LocalDocument(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), mime, modified)
                    }
                }
            }
        }
        visit(rootId)
        return result
    }

    private data class LocalDocument(
        val uri: Uri,
        val mimeType: String,
        val modified: Long,
    )

    private fun isUnmetered(): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun isCharging(): Boolean = applicationContext.getSystemService(BatteryManager::class.java).isCharging

    companion object {
        private const val WRITE_SAFETY_BUFFER_MS = 10_000L
        private val PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
    }
}

internal object BackupExecutionPolicy {
    fun canRun(
        debug: Boolean,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        unmetered: Boolean,
        charging: Boolean,
    ): Boolean = debug || ((!wifiOnly || unmetered) && (!chargingOnly || charging))
}

private fun CoroutineWorker.discoveryFailure(
    operation: String,
    throwable: Throwable,
): ListenableWorker.Result {
    val message = throwable.message ?: throwable::class.java.simpleName
    Log.e("OpenCloudSync", "$operation failed: $message", throwable)
    return ListenableWorker.Result.failure(workDataOf(DISCOVERY_ERROR to message))
}

const val DISCOVERY_ERROR = "discoveryError"

private fun CoroutineWorker.store() = FileBrowserStore(FileBrowserDatabase.create(applicationContext))

private fun CoroutineWorker.remote(serverUrl: String) =
    RemoteDiscoveryClient(TlsPolicy(applicationContext).applyTo(OkHttpClient.Builder().build(), serverUrl))

private data class FolderRefresh(
    val store: FileBrowserStore,
    val client: RemoteDiscoveryClient,
    val accountId: String,
    val space: SpaceEntity,
    val parentId: String?,
    val path: String,
    val authorization: String,
)

private suspend fun refreshFolder(request: FolderRefresh) {
    val root = requireNotNull(request.space.rootWebDavUrl) { "The space WebDAV URL is unavailable." }
    val now = System.currentTimeMillis()
    val resources =
        request.client.folder(root, request.path, request.authorization).map {
            ResourceEntity(
                request.accountId,
                request.space.driveId,
                it.id,
                request.parentId,
                it.path,
                it.name,
                if (it.folder) ResourceKind.FOLDER else ResourceKind.FILE,
                it.mimeType,
                it.size,
                it.eTag,
                it.modifiedAtEpochMillis,
                it.createdAtEpochMillis.takeIf { value -> value > 0 } ?: now,
                isFavorite = it.favorite,
            )
        }
    request.store.replaceFolderSnapshot(request.accountId, request.space.driveId, request.parentId, resources)
}
