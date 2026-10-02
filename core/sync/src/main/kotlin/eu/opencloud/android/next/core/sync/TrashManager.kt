package eu.opencloud.android.next.core.sync

import android.content.Context
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.network.RemoteTrashResource
import eu.opencloud.android.next.core.network.TransferHttpException
import eu.opencloud.android.next.core.network.WebDavFeatureClient
import eu.opencloud.android.next.core.security.TlsPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class TrashManager(
    private val context: Context,
    private val store: FileBrowserStore = FileBrowserStore(FileBrowserDatabase.create(context)),
) {
    suspend fun load(accountId: String): List<RemoteTrashResource>? {
        val account = requireNotNull(store.account(accountId)) { "The account is unavailable." }
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        val httpClient = httpClient(account.serverUrl)
        val client = WebDavFeatureClient(httpClient)
        val spaces =
            store
                .spaces(accountId)
                .filterNot { it.isDisabled || it.isDeleted }
        if (spaces.isEmpty()) return if (account.trashSupported) emptyList() else null

        var supportedEndpointFound = false
        val resources =
            spaces.flatMap { space ->
                val root = space.rootWebDavUrl ?: return@flatMap emptyList()
                try {
                    client.trash(root, space.driveId, authorization).also { supportedEndpointFound = true }
                } catch (exception: TransferHttpException) {
                    if (exception.statusCode in UNSUPPORTED_STATUS_CODES) emptyList() else throw exception
                }
            }
        return resources
            .distinctBy { "${it.spaceId}:${it.id}" }
            .sortedByDescending(RemoteTrashResource::deletedAtEpochMillis)
            .takeIf { supportedEndpointFound || account.trashSupported }
    }

    suspend fun restore(
        accountId: String,
        resource: RemoteTrashResource,
    ) {
        val account = requireNotNull(store.account(accountId)) { "The account is unavailable." }
        val space = requireNotNull(store.space(accountId, resource.spaceId)) { "The space is unavailable." }
        check(!space.isDisabled && !space.isDeleted) { "The space is unavailable." }
        store.requireAllowedVaultPath(accountId, resource.spaceId, resource.originalPath, resource.folder)
        val root = requireNotNull(space.rootWebDavUrl) { "The destination WebDAV URL is unavailable." }
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        val httpClient = httpClient(account.serverUrl)
        WebDavFeatureClient(httpClient).restore(
            root,
            resource.id,
            root.childUrl(resource.originalPath),
            authorization,
        )
        val parentPath = resource.originalPath.substringBeforeLast('/', "").ifBlank { "/" }
        val parent = store.resourceAtPath(accountId, resource.spaceId, parentPath)
        TransferManager(context, store).refreshFolder(accountId, resource.spaceId, parent?.remoteId)
    }

    suspend fun permanentlyDelete(
        accountId: String,
        resource: RemoteTrashResource,
    ) {
        val account = requireNotNull(store.account(accountId)) { "The account is unavailable." }
        val space = requireNotNull(store.space(accountId, resource.spaceId)) { "The space is unavailable." }
        check(!space.isDisabled && !space.isDeleted) { "The space is unavailable." }
        val root = requireNotNull(space.rootWebDavUrl) { "The space WebDAV URL is unavailable." }
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        val httpClient = httpClient(account.serverUrl)
        WebDavFeatureClient(httpClient).permanentlyDelete(
            root,
            resource.id,
            authorization,
        )
    }

    private fun httpClient(serverUrl: String) = TlsPolicy(context).applyTo(OkHttpClient.Builder().build(), serverUrl)

    private companion object {
        val UNSUPPORTED_STATUS_CODES = setOf(404, 405, 501)
    }
}

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
