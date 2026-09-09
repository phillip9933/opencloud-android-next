package eu.opencloud.android.next.core.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.opencloud.android.next.core.database.AccountEntity
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.datastore.SettingsRepository
import eu.opencloud.android.next.core.network.TransferClient
import eu.opencloud.android.next.core.network.TransferConflictException
import eu.opencloud.android.next.core.network.TransferHttpException
import eu.opencloud.android.next.core.network.TusOffsetException
import eu.opencloud.android.next.core.security.TlsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

abstract class TransferWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    protected val store = FileBrowserStore(FileBrowserDatabase.create(context))

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    final override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val id = inputData.getString(TRANSFER_ID) ?: return@withContext Result.failure()
            val transfer = store.transfer(id) ?: return@withContext Result.failure()
            val account =
                store.account(transfer.accountId) ?: return@withContext fail(transfer, "The account is unavailable.")
            val space =
                store.space(transfer.accountId, transfer.spaceId)
                    ?: return@withContext fail(transfer, "The space is unavailable.")
            val running =
                transfer.copy(
                    state = TransferState.RUNNING.name,
                    attemptCount = runAttemptCount,
                    updatedAtEpochMillis = now(),
                )
            store.updateTransfer(running)
            try {
                if (running.bytesTotal >= TransferNotifications.LARGE_TRANSFER_BYTES) {
                    setForeground(
                        TransferNotifications.foregroundInfo(
                            applicationContext,
                            running.id,
                            running.displayName,
                            running.bytesTransferred,
                            running.bytesTotal,
                        ),
                    )
                }
                execute(
                    running,
                    account,
                    space,
                    client(account),
                    WorkerAuthorizationProvider(applicationContext).authorization(account),
                )
                val latest = store.transfer(running.id) ?: running
                store.updateTransfer(
                    latest.copy(
                        state = TransferState.SUCCEEDED.name,
                        bytesTransferred = running.bytesTotal,
                        updatedAtEpochMillis = now(),
                    ),
                )
                deleteSourceAfterSuccess(running)
                notifyDocumentsProvider()
                Result.success()
            } catch (_: TransferConflictException) {
                val latest = store.transfer(running.id) ?: running
                store.updateTransfer(
                    latest.copy(
                        state = TransferState.CONFLICT.name,
                        error = "An item with this name already exists.",
                        updatedAtEpochMillis = now(),
                    ),
                )
                Result.failure()
            } catch (error: Throwable) {
                val latest = store.transfer(running.id) ?: running
                if (error.isRetryable() && runAttemptCount < MAX_ATTEMPTS) {
                    store.updateTransfer(
                        latest.copy(
                            state = TransferState.RETRY.name,
                            error = safeMessage(error),
                            updatedAtEpochMillis = now(),
                        ),
                    )
                    Result.retry()
                } else {
                    fail(latest, safeMessage(error))
                }
            }
        }

    protected abstract suspend fun execute(
        transfer: TransferEntity,
        account: AccountEntity,
        space: SpaceEntity,
        client: TransferClient,
        authorization: String,
    )

    protected suspend fun checkpoint(
        transfer: TransferEntity,
        bytes: Long,
        tusUrl: String? = transfer.tusUrl,
    ) {
        store.updateTransfer(
            transfer.copy(bytesTransferred = bytes, tusOffset = bytes, tusUrl = tusUrl, updatedAtEpochMillis = now()),
        )
        if (transfer.bytesTotal >= TransferNotifications.LARGE_TRANSFER_BYTES) {
            setForeground(
                TransferNotifications.foregroundInfo(
                    applicationContext,
                    transfer.id,
                    transfer.displayName,
                    bytes,
                    transfer.bytesTotal,
                ),
            )
        }
    }

    protected fun updateForegroundProgress(
        transfer: TransferEntity,
        bytes: Long,
    ) {
        if (transfer.bytesTotal >= TransferNotifications.LARGE_TRANSFER_BYTES) {
            setForegroundAsync(
                TransferNotifications.foregroundInfo(
                    applicationContext,
                    transfer.id,
                    transfer.displayName,
                    bytes,
                    transfer.bytesTotal,
                ),
            )
        }
    }

    private suspend fun fail(
        transfer: TransferEntity,
        message: String,
    ): Result {
        store.updateTransfer(
            transfer.copy(state = TransferState.FAILED.name, error = message, updatedAtEpochMillis = now()),
        )
        return Result.failure()
    }

    private fun client(account: AccountEntity): TransferClient {
        val base = OkHttpClient.Builder().build()
        return TransferClient(TlsPolicy(applicationContext).applyTo(base, account.serverUrl))
    }

    private fun Throwable.isRetryable() =
        this is java.io.IOException ||
            this is TusOffsetException ||
            (this is TransferHttpException && (statusCode == 429 || statusCode >= 500))

    private fun safeMessage(error: Throwable) =
        when (error) {
            is TransferHttpException -> "The server returned HTTP ${error.statusCode}."
            else -> error.message ?: "The transfer could not be completed."
        }

    protected fun now() = System.currentTimeMillis()

    private fun deleteSourceAfterSuccess(transfer: TransferEntity) {
        if (!transfer.deleteSourceAfterSuccess) return
        runCatching {
            val source = Uri.parse(transfer.sourceUri)
            if (source.scheme == "content") {
                DocumentsContract.deleteDocument(applicationContext.contentResolver, source)
            } else if (source.scheme == "file") {
                File(requireNotNull(source.path)).delete()
            }
        }
    }

    private fun notifyDocumentsProvider() {
        applicationContext.contentResolver.notifyChange(
            DocumentsContract.buildRootsUri("${applicationContext.packageName}.documents"),
            null,
        )
    }

    companion object {
        const val TRANSFER_ID = "transferId"
        private const val MAX_ATTEMPTS = 5
    }
}

class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : TransferWorker(context, params) {
    override suspend fun execute(
        transfer: TransferEntity,
        account: AccountEntity,
        space: SpaceEntity,
        client: TransferClient,
        authorization: String,
    ) {
        val sourceUri = Uri.parse(requireNotNull(transfer.sourceUri))
        val root = webDavRoot(account, space)
        val destinationUrl = root.childUrl(transfer.destinationPath)
        val source = {
            applicationContext.contentResolver.openInputStream(sourceUri)
                ?: error("The selected file is no longer readable.")
        }
        val upload: suspend () -> String? = {
            if (account.tusSupported && transfer.bytesTotal >= TUS_THRESHOLD) {
                uploadTus(
                    transfer,
                    account,
                    root.childUrl(transfer.destinationPath.substringBeforeLast('/', "")),
                    client,
                    source,
                )
                null
            } else {
                client.upload(
                    destinationUrl,
                    authorization,
                    transfer.mimeType,
                    transfer.bytesTotal,
                    transfer.overwrite,
                    source,
                ) { bytes -> updateForegroundProgress(transfer, bytes) }
            }
        }
        val eTag =
            try {
                upload()
            } catch (exception: TransferHttpException) {
                if (exception.statusCode != 404) throw exception
                createMissingDestinationDirectories(
                    root = root,
                    destinationPath = transfer.destinationPath,
                    client = client,
                    authorization = authorization,
                )
                upload()
            }
        checkpoint(transfer, transfer.bytesTotal)
        store.completeUpload(transfer, eTag)
    }

    private fun createMissingDestinationDirectories(
        root: String,
        destinationPath: String,
        client: TransferClient,
        authorization: String,
    ) {
        destinationCollectionPaths(destinationPath).forEach { path ->
            client.createCollection(root.childUrl(path), authorization)
        }
    }

    private suspend fun uploadTus(
        transfer: TransferEntity,
        account: AccountEntity,
        collectionUrl: String,
        client: TransferClient,
        source: () -> java.io.InputStream,
    ) {
        val metadata = "filename ${Base64.getEncoder().encodeToString(transfer.displayName.toByteArray())}"
        var authorization = WorkerAuthorizationProvider(applicationContext).authorization(account)
        val url = transfer.tusUrl ?: client.createTusUpload(collectionUrl, authorization, transfer.bytesTotal, metadata)
        var offset = if (transfer.tusUrl == null) 0 else client.tusOffset(url, authorization)
        checkpoint(transfer, offset, url)
        while (offset < transfer.bytesTotal) {
            authorization = WorkerAuthorizationProvider(applicationContext).authorization(account)
            val remaining = minOf(transfer.bytesTotal - offset, TUS_CHUNK_BYTES)
            val input = source().also { it.skipFully(offset) }
            val next = client.patchTus(url, authorization, offset, remaining, { input }) {}
            require(next > offset && next <= transfer.bytesTotal) { "The TUS server returned an invalid offset." }
            offset = next
            checkpoint(transfer, offset, url)
        }
    }

    private companion object {
        const val TUS_THRESHOLD = 10L * 1024 * 1024
        const val TUS_CHUNK_BYTES = 10L * 1024 * 1024
    }
}

internal fun destinationCollectionPaths(destinationPath: String): List<String> {
    val segments =
        destinationPath
            .trim('/')
            .split('/')
            .filter(String::isNotBlank)
            .dropLast(1)
    return segments.indices.map { index -> "/${segments.take(index + 1).joinToString("/")}" }
}

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : TransferWorker(context, params) {
    override suspend fun execute(
        transfer: TransferEntity,
        account: AccountEntity,
        space: SpaceEntity,
        client: TransferClient,
        authorization: String,
    ) {
        val resourceId = requireNotNull(transfer.resourceId)
        val cacheDir =
            File(
                applicationContext.filesDir,
                "resources/${safePart(account.id)}/${safePart(space.driveId)}",
            ).apply {
                mkdirs()
            }
        val target = File(cacheDir, safePart(resourceId))
        val partial = File(cacheDir, "${safePart(resourceId)}.part")
        val offset = partial.takeIf(File::exists)?.length() ?: 0
        client.download(
            webDavRoot(account, space).childUrl(transfer.destinationPath),
            authorization,
            offset,
        ) { input, _, resumed ->
            FileOutputStream(partial, resumed).use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = if (resumed) offset else 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    updateForegroundProgress(transfer, downloaded)
                }
            }
        }
        checkpoint(transfer, partial.length())
        require(partial.length() == transfer.bytesTotal || transfer.bytesTotal == 0L) {
            "The downloaded file size did not match the server metadata."
        }
        if (target.exists()) target.delete()
        require(partial.renameTo(target)) { "The downloaded file could not be published to the local cache." }
        store.updateLocalCopy(account.id, space.driveId, resourceId, target.absolutePath, transfer.offlinePin)
    }
}

class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val retentionDays =
            SettingsRepository
                .create(applicationContext)
                .settings
                .first()
                .cacheRetentionDays
        val cutoff = System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY
        File(applicationContext.cacheDir, "transfers").deleteOlderThan(cutoff)
        return Result.success()
    }
}

private fun File.deleteOlderThan(cutoffEpochMillis: Long) {
    if (!exists()) return
    walkBottomUp().forEach { file ->
        if (file.isFile && file.lastModified() < cutoffEpochMillis) file.delete()
        if (file.isDirectory && file.list().isNullOrEmpty()) file.delete()
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private fun webDavRoot(
    account: AccountEntity,
    space: SpaceEntity,
): String =
    space.rootWebDavUrl?.takeIf(String::isNotBlank)
        ?: "${account.serverUrl.trimEnd('/')}/remote.php/dav/files/${Uri.encode(account.userId)}"

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

private fun java.io.InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            if (read() < 0) error("The upload source changed while resuming.")
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

private fun safePart(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
