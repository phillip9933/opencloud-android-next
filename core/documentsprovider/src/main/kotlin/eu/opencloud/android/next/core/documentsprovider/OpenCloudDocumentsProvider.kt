package eu.opencloud.android.next.core.documentsprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import eu.opencloud.android.next.core.database.AccountEntity
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.sync.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.util.Base64

class OpenCloudDocumentsProvider : DocumentsProvider() {
    private val store by lazy {
        FileBrowserStore(FileBrowserDatabase.create(requireNotNull(context)))
    }
    private val transfers by lazy { TransferManager(requireNotNull(context), store) }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        databaseCall {
            MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION).apply {
                store.activeAccounts().forEach { account -> addRoot(account) }
            }
        }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor =
        databaseCall {
            MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
                when (val id = DocumentId.decode(documentId)) {
                    is DocumentId.Account -> addAccount(requireNotNull(store.account(id.accountId)))
                    is DocumentId.Space -> addSpace(requireNotNull(store.space(id.accountId, id.spaceId)))
                    is DocumentId.Resource ->
                        addResource(
                            requireNotNull(store.resource(id.accountId, id.spaceId, id.resourceId)),
                        )
                }
            }
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        databaseCall {
            MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
                when (val id = DocumentId.decode(parentDocumentId)) {
                    is DocumentId.Account -> store.spaces(id.accountId).forEach { space -> addSpace(space) }
                    is DocumentId.Space ->
                        store.children(id.accountId, id.spaceId, null).forEach { resource ->
                            addResource(resource)
                        }
                    is DocumentId.Resource -> {
                        val parent = requireNotNull(store.resource(id.accountId, id.spaceId, id.resourceId))
                        require(parent.kind == ResourceKind.FOLDER) { "The requested document is not a folder." }
                        store.children(id.accountId, id.spaceId, id.resourceId).forEach { resource ->
                            addResource(resource)
                        }
                    }
                }
            }
        }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): Cursor =
        databaseCall {
            val account =
                DocumentId.decode(rootId) as? DocumentId.Account ?: throw FileNotFoundException("Invalid root.")
            MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
                store.spaces(account.accountId).forEach { space ->
                    store
                        .resources(account.accountId, space.driveId)
                        .filter { it.name.contains(query, ignoreCase = true) }
                        .take(MAX_SEARCH_RESULTS)
                        .forEach { resource -> addResource(resource) }
                }
            }
        }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(mode == "r") { "OpenCloud documents are currently read-only through the system picker." }
        val id = DocumentId.decode(documentId) as? DocumentId.Resource ?: throw FileNotFoundException("Not a file.")
        val resource =
            databaseCall { store.resource(id.accountId, id.spaceId, id.resourceId) }
                ?: throw FileNotFoundException("Document not found.")
        require(resource.kind == ResourceKind.FILE) { "Folders cannot be opened as files." }
        resource.localPath?.let(::File)?.takeIf(File::isFile)?.let { local ->
            return ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        signal?.throwIfCanceled()
        databaseCall { transfers.enqueueDownload(resource, offlinePin = false) }
        throw FileNotFoundException("This document is downloading. Try opening it again shortly.")
    }

    override fun getDocumentType(documentId: String): String =
        databaseCall {
            when (val id = DocumentId.decode(documentId)) {
                is DocumentId.Account, is DocumentId.Space -> Document.MIME_TYPE_DIR
                is DocumentId.Resource ->
                    store.resource(id.accountId, id.spaceId, id.resourceId)?.documentMimeType()
                        ?: throw FileNotFoundException("Document not found.")
            }
        }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean =
        databaseCall {
            val parent = DocumentId.decode(parentDocumentId)
            val child = DocumentId.decode(documentId)
            when {
                parent is DocumentId.Account && child is DocumentId.Space -> parent.accountId == child.accountId
                parent is DocumentId.Space && child is DocumentId.Resource ->
                    parent.accountId == child.accountId && parent.spaceId == child.spaceId
                parent is DocumentId.Resource && child is DocumentId.Resource -> isResourceChild(parent, child)
                else -> false
            }
        }

    private suspend fun isResourceChild(
        parent: DocumentId.Resource,
        child: DocumentId.Resource,
    ): Boolean {
        if (parent.accountId != child.accountId || parent.spaceId != child.spaceId) return false
        var current = store.resource(child.accountId, child.spaceId, child.resourceId)
        var isChild = false
        while (current?.parentId != null && !isChild) {
            val parentId = requireNotNull(current.parentId)
            isChild = parentId == parent.resourceId
            if (!isChild) current = store.resource(child.accountId, child.spaceId, parentId)
        }
        return isChild
    }

    private fun MatrixCursor.addRoot(account: AccountEntity) {
        newRow()
            .add(Root.COLUMN_ROOT_ID, DocumentId.Account(account.id).encode())
            .add(Root.COLUMN_DOCUMENT_ID, DocumentId.Account(account.id).encode())
            .add(Root.COLUMN_TITLE, account.displayName)
            .add(Root.COLUMN_SUMMARY, account.serverUrl)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_SEARCH)
            .add(Root.COLUMN_MIME_TYPES, "*/*")
    }

    private fun MatrixCursor.addAccount(account: AccountEntity) {
        addDirectory(DocumentId.Account(account.id).encode(), account.displayName)
    }

    private fun MatrixCursor.addSpace(space: SpaceEntity) {
        addDirectory(DocumentId.Space(space.accountId, space.driveId).encode(), space.name)
    }

    private fun MatrixCursor.addResource(resource: ResourceEntity) {
        newRow()
            .add(
                Document.COLUMN_DOCUMENT_ID,
                DocumentId.Resource(resource.accountId, resource.spaceId, resource.remoteId).encode(),
            ).add(Document.COLUMN_DISPLAY_NAME, resource.name)
            .add(Document.COLUMN_MIME_TYPE, resource.documentMimeType())
            .add(Document.COLUMN_SIZE, resource.sizeBytes)
            .add(Document.COLUMN_LAST_MODIFIED, resource.modifiedAtEpochMillis)
            .add(Document.COLUMN_FLAGS, 0)
    }

    private fun MatrixCursor.addDirectory(
        id: String,
        name: String,
    ) {
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, name)
            .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            .add(Document.COLUMN_FLAGS, 0)
    }

    private fun ResourceEntity.documentMimeType() =
        if (kind == ResourceKind.FOLDER) Document.MIME_TYPE_DIR else mimeType ?: "application/octet-stream"

    private fun <T> databaseCall(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    private sealed interface DocumentId {
        data class Account(
            val accountId: String,
        ) : DocumentId

        data class Space(
            val accountId: String,
            val spaceId: String,
        ) : DocumentId

        data class Resource(
            val accountId: String,
            val spaceId: String,
            val resourceId: String,
        ) : DocumentId

        fun encode(): String {
            val raw =
                when (this) {
                    is Account -> listOf(VERSION, "account", accountId)
                    is Space -> listOf(VERSION, "space", accountId, spaceId)
                    is Resource -> listOf(VERSION, "resource", accountId, spaceId, resourceId)
                }.joinToString("\u0000")
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        }

        companion object {
            fun decode(encoded: String): DocumentId {
                val values =
                    runCatching {
                        String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\u0000")
                    }.getOrElse { invalidDocumentId() }
                if (values.firstOrNull() != VERSION) invalidDocumentId("Unsupported document ID.")
                return when (values.getOrNull(1)) {
                    "account" -> Account(values.required(2))
                    "space" -> Space(values.required(2), values.required(3))
                    "resource" -> Resource(values.required(2), values.required(3), values.required(4))
                    else -> invalidDocumentId()
                }
            }

            private const val VERSION = "v1"
        }
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 100
        val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_TITLE,
                Root.COLUMN_SUMMARY,
                Root.COLUMN_FLAGS,
                Root.COLUMN_MIME_TYPES,
            )
        val DEFAULT_DOCUMENT_PROJECTION =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
            )
    }
}

private fun List<String>.required(index: Int): String =
    getOrNull(index)?.takeIf(String::isNotBlank) ?: invalidDocumentId()

private fun invalidDocumentId(message: String = "Invalid document ID."): Nothing = throw FileNotFoundException(message)
