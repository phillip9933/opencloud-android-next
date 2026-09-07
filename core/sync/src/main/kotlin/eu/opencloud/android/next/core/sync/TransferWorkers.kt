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
import eu.opencloud.android.next.core.network.TransferClient
import eu.opencloud.android.next.core.network.TransferConflictException
import eu.opencloud.android.next.core.network.TransferHttpException
import eu.opencloud.android.next.core.network.TusOffsetException
import eu.opencloud.android.next.core.security.KeystoreCredentialStore
import eu.opencloud.android.next.core.security.TlsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

abstract class TransferWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    protected val store = FileBrowserStore(FileBrowserDatabase.create(context))

    @Suppress("TooGenericExceptionCaught")
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
                execute(running, account, space, client(account), authorization(account))
                val latest = store.transfer(running.id) ?: running
                store.updateTransfer(
                    latest.copy(
                        state = TransferState.SUCCEEDED.name,
                        bytesTransferred = running.bytesTotal,
                        updatedAtEpochMillis = now(),
                    ),
                )
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

    private fun authorization(account: AccountEntity): String {
        val credentials = KeystoreCredentialStore(applicationContext)
        return if (account.authenticationType == "BASIC") {
            Credentials.basic(account.userId, requireNotNull(credentials.readBasicPassword(account.id)))
        } else {
            val tokens = requireNotNull(credentials.readTokens(account.id))
            require(
                tokens.expiresAtEpochSeconds > System.currentTimeMillis() / 1000,
            ) { "Sign in again to continue transfers." }
            "${tokens.tokenType} ${tokens.accessToken}"
        }
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
        val eTag =
            if (account.tusSupported && transfer.bytesTotal >= TUS_THRESHOLD) {
                uploadTus(
                    transfer,
                    root.childUrl(transfer.destinationPath.substringBeforeLast('/', "")),
                    client,
                    authorization,
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
                ) {}
            }
        checkpoint(transfer, transfer.bytesTotal)
        store.completeUpload(transfer, eTag)
    }

    private suspend fun uploadTus(
        transfer: TransferEntity,
        collectionUrl: String,
        client: TransferClient,
        authorization: String,
        source: () -> java.io.InputStream,
    ) {
        val metadata = "filename ${Base64.getEncoder().encodeToString(transfer.displayName.toByteArray())}"
        val url = transfer.tusUrl ?: client.createTusUpload(collectionUrl, authorization, transfer.bytesTotal, metadata)
        var offset = if (transfer.tusUrl == null) 0 else client.tusOffset(url, authorization)
        checkpoint(transfer, offset, url)
        while (offset < transfer.bytesTotal) {
            val remaining = transfer.bytesTotal - offset
            val input = source().also { it.skipFully(offset) }
            val next = client.patchTus(url, authorization, offset, remaining, { input }) {}
            require(next > offset && next <= transfer.bytesTotal) { "The TUS server returned an invalid offset." }
            offset = next
            checkpoint(transfer, offset, url)
        }
    }

    private companion object {
        const val TUS_THRESHOLD = 10L * 1024 * 1024
    }
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
        File(applicationContext.cacheDir, "transfers").deleteRecursively()
        FileBrowserStore(FileBrowserDatabase.create(applicationContext)).deleteSuccessfulTransfers(
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7),
        )
        return Result.success()
    }
}

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
