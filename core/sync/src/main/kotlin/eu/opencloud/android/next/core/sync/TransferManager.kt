package eu.opencloud.android.next.core.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.TransferDirection
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.network.GraphFavoriteClient
import eu.opencloud.android.next.core.network.TransferClient
import eu.opencloud.android.next.core.network.WebDavFeatureClient
import eu.opencloud.android.next.core.security.TlsPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

class TransferManager(
    private val context: Context,
    private val store: FileBrowserStore = FileBrowserStore(FileBrowserDatabase.create(context)),
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {
    suspend fun enqueueUpload(
        accountId: String,
        spaceId: String,
        parentPath: String?,
        source: Uri,
        deleteSourceAfterSuccess: Boolean = false,
    ): String {
        require(source.scheme == "content" || source.scheme == "file") { "Choose a readable local file." }
        val metadata = sourceMetadata(source)
        val name = metadata.name.requireValidSegment()
        val destination = "${parentPath?.trimEnd('/').orEmpty()}/$name"
        store.activeUpload(accountId, spaceId, source.toString(), destination)?.let { return it.id }
        val now = System.currentTimeMillis()
        val transfer =
            TransferEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                spaceId = spaceId,
                resourceId = null,
                direction = TransferDirection.UPLOAD.name,
                sourceUri = source.toString(),
                destinationPath = destination,
                displayName = name,
                mimeType = metadata.mimeType,
                bytesTotal = metadata.size,
                deleteSourceAfterSuccess = deleteSourceAfterSuccess,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        store.createTransfer(transfer)
        enqueueUploadWork(transfer)
        return transfer.id
    }

    suspend fun enqueueDownload(
        resource: ResourceEntity,
        offlinePin: Boolean,
    ): String {
        require(resource.kind.name == "FILE") { "Only files can be downloaded." }
        store.activeDownload(resource.accountId, resource.spaceId, resource.remoteId)?.let { return it.id }
        val now = System.currentTimeMillis()
        val transfer =
            TransferEntity(
                id = UUID.randomUUID().toString(),
                accountId = resource.accountId,
                spaceId = resource.spaceId,
                resourceId = resource.remoteId,
                direction = TransferDirection.DOWNLOAD.name,
                sourceUri = null,
                destinationPath = resource.path,
                displayName = resource.name,
                mimeType = resource.mimeType,
                bytesTotal = resource.sizeBytes,
                offlinePin = offlinePin,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        store.createTransfer(transfer)
        enqueueDownloadWork(transfer)
        return transfer.id
    }

    suspend fun makeAvailableOffline(resource: ResourceEntity) {
        store.setOfflinePinned(resource, true)
        if (resource.kind.name == "FOLDER") {
            val request =
                OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                    .setInputData(
                        workDataOf(
                            OfflineSyncWorker.ACCOUNT_ID to resource.accountId,
                            OfflineSyncWorker.SPACE_ID to resource.spaceId,
                            OfflineSyncWorker.RESOURCE_ID to resource.remoteId,
                        ),
                    ).setConstraints(networkConstraints())
                    .addTag(accountWorkTag(resource.accountId))
                    .build()
            workManager.enqueueUniqueWork(
                "offline-${resource.accountId}-${resource.spaceId}-${resource.remoteId}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        } else {
            enqueueDownload(resource, true)
        }
    }

    suspend fun createFolder(
        accountId: String,
        spaceId: String,
        parentId: String?,
        name: String,
    ) {
        val normalizedName = name.trim().requireValidSegment()
        val account = requireNotNull(store.account(accountId)) { "The account is unavailable." }
        val space = requireNotNull(store.space(accountId, spaceId)) { "The space is unavailable." }
        val parent = parentId?.let { requireNotNull(store.resource(accountId, spaceId, it)) }
        require(parent == null || parent.kind == ResourceKind.FOLDER) { "Choose a folder as the parent." }
        val destinationPath = "${parent?.path?.trimEnd('/').orEmpty()}/$normalizedName"
        val root =
            space.rootWebDavUrl?.takeIf(String::isNotBlank)
                ?: "${account.serverUrl.trimEnd('/')}/remote.php/dav/files/${Uri.encode(account.userId)}"
        val client = TransferClient(TlsPolicy(context).applyTo(OkHttpClient.Builder().build(), account.serverUrl))
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        client.createCollection(root.childUrl(destinationPath), authorization)
        store.createFolder(accountId, spaceId, parentId, normalizedName)
    }

    fun refreshAccount(accountId: String): UUID {
        val request =
            OneTimeWorkRequestBuilder<AccountDiscoveryWorker>()
                .setInputData(
                    workDataOf(
                        AccountDiscoveryWorker.ACCOUNT_ID to accountId,
                    ),
                ).setConstraints(networkConstraints())
                .addTag(accountWorkTag(accountId))
                .build()
        workManager.enqueueUniqueWork("discover-$accountId", ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    fun refreshFolder(
        accountId: String,
        spaceId: String,
        folderId: String?,
    ): UUID {
        val request =
            OneTimeWorkRequestBuilder<FolderDiscoveryWorker>()
                .setInputData(
                    workDataOf(
                        FolderDiscoveryWorker.ACCOUNT_ID to accountId,
                        FolderDiscoveryWorker.SPACE_ID to spaceId,
                        FolderDiscoveryWorker.FOLDER_ID to folderId,
                    ),
                ).setConstraints(networkConstraints())
                .addTag(accountWorkTag(accountId))
                .build()
        workManager.enqueueUniqueWork(
            "folder-$accountId-$spaceId-${folderId ?: "root"}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id
    }

    suspend fun retryConflict(
        transfer: TransferEntity,
        overwrite: Boolean,
        keepBoth: Boolean = false,
    ) {
        val destination = if (keepBoth) conflictCopyPath(transfer.destinationPath) else transfer.destinationPath
        val reset =
            transfer.copy(
                destinationPath = destination,
                displayName = destination.substringAfterLast('/'),
                state = TransferState.QUEUED.name,
                error = null,
                overwrite = overwrite,
                workId = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        store.updateTransfer(reset)
        enqueueUploadWork(reset, ExistingWorkPolicy.REPLACE)
    }

    suspend fun retry(transfer: TransferEntity) {
        require(transfer.state == TransferState.FAILED.name || transfer.state == TransferState.CANCELLED.name) {
            "Only failed or cancelled transfers can be retried."
        }
        val reset =
            transfer.copy(
                state = TransferState.QUEUED.name,
                error = null,
                workId = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        store.updateTransfer(reset)
        if (reset.direction == TransferDirection.UPLOAD.name) {
            enqueueUploadWork(reset, ExistingWorkPolicy.REPLACE)
        } else {
            enqueueDownloadWork(reset, ExistingWorkPolicy.REPLACE)
        }
    }

    suspend fun cancel(transfer: TransferEntity) {
        transfer.workId?.let { workManager.cancelWorkById(UUID.fromString(it)) }
        store.updateTransfer(
            transfer.copy(
                state = TransferState.CANCELLED.name,
                error = null,
                workId = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearHistory(accountId: String) = store.clearTransferHistory(accountId)

    suspend fun clearAll(
        accountId: String,
        transfers: List<TransferEntity>,
    ) {
        transfers.mapNotNull(TransferEntity::workId).forEach { workId ->
            runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
        }
        store.clearTransfers(accountId)
    }

    suspend fun cancelConflict(transfer: TransferEntity) {
        store.updateTransfer(
            transfer.copy(
                state = TransferState.CANCELLED.name,
                error = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveBackup(configuration: eu.opencloud.android.next.core.database.FolderBackupEntity) {
        store.saveBackup(configuration)
        scheduleBackups()
        val immediate =
            OneTimeWorkRequestBuilder<FolderBackupScanWorker>()
                .setConstraints(backupConstraints(BuildConfig.DEBUG))
                .build()
        workManager.enqueueUniqueWork(BACKUP_SCAN_WORK, ExistingWorkPolicy.REPLACE, immediate)
    }

    fun scheduleBackups() {
        val request =
            PeriodicWorkRequestBuilder<FolderBackupScanWorker>(
                15,
                TimeUnit.MINUTES,
            ).setConstraints(backupConstraints(BuildConfig.DEBUG)).build()
        workManager.enqueueUniquePeriodicWork(BACKUP_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    suspend fun reconcile() {
        store.pendingTransfers().forEach { transfer ->
            if (transfer.direction ==
                TransferDirection.UPLOAD.name
            ) {
                enqueueUploadWork(transfer)
            } else {
                enqueueDownloadWork(transfer)
            }
        }
    }

    suspend fun setFavorite(
        resource: ResourceEntity,
        favorite: Boolean,
    ) {
        val account = requireNotNull(store.account(resource.accountId))
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        GraphFavoriteClient(TlsPolicy(context).applyTo(OkHttpClient.Builder().build(), account.serverUrl))
            .setFavorite(account.serverUrl, resource.remoteId, authorization, favorite)
        store.setFavorite(resource, favorite)
    }

    suspend fun delete(resource: ResourceEntity) {
        val account = requireNotNull(store.account(resource.accountId))
        val space = requireNotNull(store.space(resource.accountId, resource.spaceId))
        val root = requireNotNull(space.rootWebDavUrl) { "The space WebDAV URL is unavailable." }
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        WebDavFeatureClient(TlsPolicy(context).applyTo(OkHttpClient.Builder().build(), account.serverUrl))
            .delete(root.childUrl(resource.path), authorization)
        store.delete(resource.accountId, resource.spaceId, resource.remoteId)
    }

    suspend fun cancelAccountWork(accountId: String) {
        store
            .activeTransfers(accountId)
            .mapNotNull { it.workId }
            .mapNotNull { workId ->
                runCatching { UUID.fromString(workId) }.getOrNull()
            }.forEach(workManager::cancelWorkById)
        workManager.cancelAllWorkByTag(accountWorkTag(accountId))
    }

    fun scheduleCleanup() {
        val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(CLEANUP_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        val offline =
            PeriodicWorkRequestBuilder<OfflineSyncWorker>(
                15,
                TimeUnit.MINUTES,
            ).setConstraints(networkConstraints()).build()
        workManager.enqueueUniquePeriodicWork(OFFLINE_WORK, ExistingPeriodicWorkPolicy.UPDATE, offline)
        scheduleBackups()
    }

    private suspend fun enqueueUploadWork(
        transfer: TransferEntity,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val request =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(TransferWorker.TRANSFER_ID to transfer.id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(accountWorkTag(transfer.accountId))
                .build()
        store.updateTransfer(
            transfer.copy(workId = request.id.toString(), updatedAtEpochMillis = System.currentTimeMillis()),
        )
        workManager.enqueueUniqueWork("transfer-${transfer.id}", policy, request)
    }

    private suspend fun enqueueDownloadWork(
        transfer: TransferEntity,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(TransferWorker.TRANSFER_ID to transfer.id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(accountWorkTag(transfer.accountId))
                .build()
        store.updateTransfer(
            transfer.copy(workId = request.id.toString(), updatedAtEpochMillis = System.currentTimeMillis()),
        )
        workManager.enqueueUniqueWork("transfer-${transfer.id}", policy, request)
    }

    private fun sourceMetadata(uri: Uri): SourceMetadata {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
        var size = -1L
        if (uri.scheme == "content") {
            queryContentMetadata(uri)?.let { metadata ->
                name = metadata.name ?: name
                size = metadata.size ?: size
            }
        }
        if (size < 0) {
            size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }
        require(size >= 0) { "The selected file size could not be determined." }
        return SourceMetadata(name, context.contentResolver.getType(uri), size)
    }

    private fun queryContentMetadata(uri: Uri): ContentMetadata? =
        context.contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ContentMetadata(
                    name = cursor.getString(0),
                    size = if (cursor.isNull(1)) null else cursor.getLong(1),
                )
            }

    private data class SourceMetadata(
        val name: String,
        val mimeType: String?,
        val size: Long,
    )

    private data class ContentMetadata(
        val name: String?,
        val size: Long?,
    )

    private companion object {
        const val CLEANUP_WORK = "opencloud-cache-cleanup"
        const val OFFLINE_WORK = "opencloud-offline-sync"
        const val BACKUP_WORK = "opencloud-folder-backups"
        const val BACKUP_SCAN_WORK = "opencloud-folder-backup-scan"
    }
}

fun accountWorkTag(accountId: String) = "opencloud-account-$accountId"

private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

private fun String.childUrl(path: String): String =
    toHttpUrl()
        .newBuilder()
        .apply {
            path
                .trim('/')
                .split('/')
                .filter(String::isNotBlank)
                .forEach(::addPathSegment)
        }.build()
        .toString()

internal fun backupConstraints(debug: Boolean): Constraints =
    Constraints
        .Builder()
        .setRequiredNetworkType(if (debug) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .setRequiresCharging(!debug)
        .build()

private fun conflictCopyPath(path: String): String {
    val name = path.substringAfterLast('/')
    val parent = path.substringBeforeLast('/', "")
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    return "$parent/${name.substring(0, dot)} (conflict copy)${name.substring(dot)}"
}

private fun String.requireValidSegment(): String {
    require(isNotBlank() && '/' !in this && this != "." && this != "..") { "The selected file has an invalid name." }
    return this
}
