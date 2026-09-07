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

    fun scheduleCleanup() {
        val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(CLEANUP_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private suspend fun enqueueUploadWork(transfer: TransferEntity) {
        val request =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(TransferWorker.TRANSFER_ID to transfer.id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        store.updateTransfer(
            transfer.copy(workId = request.id.toString(), updatedAtEpochMillis = System.currentTimeMillis()),
        )
        workManager.enqueueUniqueWork("transfer-${transfer.id}", ExistingWorkPolicy.KEEP, request)
    }

    private suspend fun enqueueDownloadWork(transfer: TransferEntity) {
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(TransferWorker.TRANSFER_ID to transfer.id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        store.updateTransfer(
            transfer.copy(workId = request.id.toString(), updatedAtEpochMillis = System.currentTimeMillis()),
        )
        workManager.enqueueUniqueWork("transfer-${transfer.id}", ExistingWorkPolicy.KEEP, request)
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
    }
}

private fun String.requireValidSegment(): String {
    require(isNotBlank() && '/' !in this && this != "." && this != "..") { "The selected file has an invalid name." }
    return this
}
