package eu.opencloud.android.next.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fresh access evidence for one remote item. Never promotes cached recipient grants into caller rights. */
class SharedFolderAccessClient(
    client: OkHttpClient,
    private val endpoints: EndpointPolicy = EndpointPolicy(),
) {
    private val client =
        client
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Mount discovery supplies identity only; fresh item metadata still supplies the address and rights. */
    fun resolveWithMounts(
        serverUrl: String,
        authorization: String,
        share: IncomingSharedItem,
    ): SharedFolderResolution {
        val remote = share.remoteItem
        val driveId =
            remote.parentReference?.driveId?.takeIf(String::isNotBlank)
                ?: SharedFolderMountClient(client, endpoints).remoteDriveId(serverUrl, authorization, remote.id)
                ?: return SharedFolderResolution.Unresolved(SharedFolderMissing.IDENTITY)
        val identified =
            share.copy(
                remoteItem =
                    remote.copy(
                        parentReference =
                            (remote.parentReference ?: SharedParentReference()).copy(
                                driveId = driveId,
                            ),
                    ),
            )
        return resolve(serverUrl, authorization, identified)
    }

    @Suppress("ReturnCount") // Stop before using missing identities or unavailable remote items.
    fun resolve(
        serverUrl: String,
        authorization: String,
        share: IncomingSharedItem,
    ): SharedFolderResolution {
        val driveId = share.remoteItem.parentReference?.driveId
        if (driveId.isNullOrBlank()) return SharedFolderResolution.Unresolved(SharedFolderMissing.IDENTITY)
        validateIdentity(driveId)
        validateIdentity(share.remoteItem.id)
        val origin = endpoints.endpoint(serverUrl, allowQuery = false)
        val url =
            origin
                .newBuilder()
                .addPathSegments("graph/v1beta1/drives")
                .addPathSegment(driveId)
                .addPathSegment("items")
                .addPathSegment(share.remoteItem.id)
                .addQueryParameter("\$select", "@libre.graph.permissions.actions.allowedValues")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .get()
                .build()
        val item =
            client.newCall(request).execute().use { response ->
                if (response.code in setOf(403, 404, 410)) return SharedFolderResolution.Unavailable
                if (!response.isSuccessful) {
                    throw TransferHttpException(response.code, parseRetryAfter(response.header("Retry-After")))
                }
                val source = response.body?.source() ?: invalid()
                source.request(MAX_ITEM_BYTES + 1)
                if (source.buffer.size > MAX_ITEM_BYTES) invalid()
                decode(source.readUtf8())
            }
        if (item.id != share.remoteItem.id || item.parentReference?.driveId?.let { it != driveId } == true) invalid()
        return resolveItem(origin.toString(), driveId, item)
    }

    @Suppress("ReturnCount") // Keep unresolved evidence separate from denied access and trusted roots.
    private fun resolveItem(
        serverUrl: String,
        driveId: String,
        item: SharedFolderMetadata,
    ): SharedFolderResolution {
        if (item.deleted != null) return SharedFolderResolution.Unavailable
        if (item.folder == null || item.file != null) {
            return SharedFolderResolution.Unresolved(SharedFolderMissing.FOLDER)
        }
        val root = item.webDavUrl ?: return SharedFolderResolution.Unresolved(SharedFolderMissing.DAV_ROOT)
        val dav = endpoints.endpoint(root, allowQuery = false)
        val origin = endpoints.endpoint(serverUrl)
        if (dav.scheme != origin.scheme || dav.host != origin.host || dav.port != origin.port) {
            throw OpenCloudException(OpenCloudError.Trust)
        }
        val actions =
            item.effectiveActions
                ?: return SharedFolderResolution.Unresolved(SharedFolderMissing.EFFECTIVE_ACCESS)
        return SharedFolderResolution.Resolved(driveId, item.id, dav.toString(), SharedFolderAccess(actions))
    }

    private fun decode(body: String): SharedFolderMetadata =
        try {
            json.decodeFromString<SharedFolderMetadata>(body)
        } catch (_: SerializationException) {
            invalid()
        } catch (_: IllegalArgumentException) {
            invalid()
        }

    private fun validateIdentity(value: String) {
        if (value.isBlank() || value == "." || value == "..") invalid()
    }

    private fun invalid(): Nothing = throw OpenCloudException(OpenCloudError.InvalidResponse)

    private companion object {
        const val MAX_ITEM_BYTES = 1024 * 1024L
    }
}

sealed interface SharedFolderResolution {
    data class Resolved(
        val driveId: String,
        val itemId: String,
        val webDavUrl: String,
        val access: SharedFolderAccess,
    ) : SharedFolderResolution

    data class Unresolved(
        val missing: SharedFolderMissing,
    ) : SharedFolderResolution

    data object Unavailable : SharedFolderResolution
}

enum class SharedFolderMissing { IDENTITY, FOLDER, DAV_ROOT, EFFECTIVE_ACCESS }

/** Rights apply to this folder only; descendants must still be checked by the server. */
data class SharedFolderAccess(
    val actions: Set<String>,
) {
    val canBrowse: Boolean get() = "libre.graph/driveItem/children/read" in actions
    val canReadContent: Boolean get() = "libre.graph/driveItem/content/read" in actions
    val canUpload: Boolean get() = "libre.graph/driveItem/upload/create" in actions
    val canCreateFolder: Boolean get() = "libre.graph/driveItem/children/create" in actions
}

@Serializable
private data class SharedFolderMetadata(
    val id: String,
    val folder: JsonObject? = null,
    val file: JsonObject? = null,
    val deleted: JsonObject? = null,
    val webDavUrl: String? = null,
    val parentReference: SharedParentReference? = null,
    @SerialName("@libre.graph.permissions.actions.allowedValues") val effectiveActions: Set<String>? = null,
)
