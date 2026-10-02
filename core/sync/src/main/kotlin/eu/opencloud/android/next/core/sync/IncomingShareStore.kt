package eu.opencloud.android.next.core.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Private, durable intake while the user chooses a destination. Never interprets shared file contents. */
class IncomingShareStore(
    private val context: Context,
) {
    private val root = File(context.noBackupFilesDir, "incoming-shares")

    fun isStaged(batchId: String): Boolean = hasSavedAtomicFile(AtomicFile(File(directory(batchId), "manifest.json")))

    fun destination(batchId: String): IncomingShareDestination? {
        val file = AtomicFile(File(directory(batchId), "destination.json"))
        if (!hasSavedAtomicFile(file)) return null
        val value = JSONObject(readSavedAtomicFile(file, 16_384).toString(Charsets.UTF_8))
        return IncomingShareDestination(value.getString("account"), value.getString("space"), value.getString("parent"))
    }

    internal fun rememberDestination(
        batchId: String,
        destination: IncomingShareDestination,
    ) {
        check(isStaged(batchId)) { "The shared files are unavailable." }
        val previous = this.destination(batchId)
        check(previous == null || previous == destination) { "Keep the original upload destination when retrying." }
        val file = AtomicFile(File(directory(batchId), "destination.json"))
        val value =
            JSONObject()
                .put("account", destination.accountId)
                .put("space", destination.spaceId)
                .put("parent", destination.parentPath)
        val output = file.startWrite()
        try {
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: java.io.IOException) {
            file.failWrite(output)
            throw failure
        }
    }

    suspend fun stage(
        batchId: String,
        sources: List<Uri>,
        checkActive: () -> Unit,
    ): List<SharedUploadSource> =
        batchGate(batchId).withLock {
            checkActive()
            val batch = directory(batchId)
            val files =
                (0 until 100).flatMap { uploadSourceFiles(File(batch, it.toString())) } +
                    listOf("manifest.json", "manifest.json.bak", "manifest.json.new").map { File(batch, it) }
            PrivateCacheUse.hold(files) { stageBatch(batchId, sources, checkActive) }
        }

    private fun stageBatch(
        batchId: String,
        sources: List<Uri>,
        checkActive: () -> Unit,
    ): List<SharedUploadSource> {
        val directory = directory(batchId)
        val manifest = AtomicFile(File(directory, "manifest.json"))
        if (hasSavedAtomicFile(manifest)) return read(directory, manifest)
        require(sources.isNotEmpty() && sources.size <= 100) { "Choose between 1 and 100 files." }
        directory.mkdirs()
        val entries = JSONArray()
        sources.forEachIndexed { index, uri ->
            checkActive()
            require(uri.scheme == "content" && uri.authority != "${context.packageName}.documents") {
                "Share readable files from another app."
            }
            val name = displayName(uri)
            val file =
                stageUploadSource(File(directory, index.toString()), -1, {
                    openUploadSource(context, uri)
                }, { directory.usableSpace }, checkActive)
            entries.put(
                JSONObject()
                    .put("id", UUID.nameUUIDFromBytes("$batchId:$index".toByteArray()).toString())
                    .put("name", name)
                    .put("mime", context.contentResolver.getType(uri))
                    .put("index", index)
                    .put("size", file.length()),
            )
        }
        val output = manifest.startWrite()
        try {
            output.write(entries.toString().toByteArray(Charsets.UTF_8))
            manifest.finishWrite(output)
        } catch (failure: java.io.IOException) {
            manifest.failWrite(output)
            throw failure
        }
        return read(directory, manifest)
    }

    suspend fun submit(
        batchId: String,
        destination: IncomingShareDestination,
        enqueue: suspend (SharedUploadSource) -> Unit,
    ) = batchGate(batchId).withLock {
        val batch = directory(batchId)
        val files = read(batch, AtomicFile(File(batch, "manifest.json")))
        rememberDestination(batchId, destination)
        files.forEach {
            currentCoroutineContext().ensureActive()
            enqueue(it)
        }
        currentCoroutineContext().ensureActive()
        removeBatch(batchId)
    }

    suspend fun discard(batchId: String) = batchGate(batchId).withLock { removeBatch(batchId) }

    private fun removeBatch(batchId: String) {
        val batch = directory(batchId)
        check(!batch.exists() || batch.deleteRecursively()) { "Could not clear the incoming files." }
    }

    private fun batchGate(batchId: String): Mutex = gates.getOrPut(directory(batchId).absolutePath) { Mutex() }

    private companion object {
        val gates = ConcurrentHashMap<String, Mutex>()
    }

    private fun directory(batchId: String): File = File(root, UUID.fromString(batchId).toString())

    private fun read(
        directory: File,
        manifest: AtomicFile,
    ): List<SharedUploadSource> {
        val entries = JSONArray(readSavedAtomicFile(manifest, 128 * 1024L).toString(Charsets.UTF_8))
        require(entries.length() in 1..100)
        return (0 until entries.length())
            .map { index ->
                val item = entries.getJSONObject(index)
                val payload = File(directory, "$index/payload")
                check(payload.isFile && payload.length() == item.getLong("size")) { "Share the files again to retry." }
                payload.setLastModified(System.currentTimeMillis())
                File(directory, "$index/seal").setLastModified(System.currentTimeMillis())
                SharedUploadSource(
                    item.getString("id"),
                    item.getString("name"),
                    item.optString("mime").takeIf(String::isNotBlank),
                    payload,
                )
            }.also { manifest.baseFile.setLastModified(System.currentTimeMillis()) }
    }

    private fun displayName(uri: Uri): String {
        val name =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
        require(name.isNotBlank() && name.length <= 255 && name !in setOf(".", "..")) { "Invalid shared filename." }
        require(name.none { it == '/' || it == '\\' || it.isISOControl() }) { "Invalid shared filename." }
        return name
    }
}

data class IncomingShareDestination(
    val accountId: String,
    val spaceId: String,
    val parentPath: String,
)
