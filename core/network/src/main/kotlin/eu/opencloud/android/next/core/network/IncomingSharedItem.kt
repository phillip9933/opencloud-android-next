package eu.opencloud.android.next.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Discovery evidence, not a writable destination. Resolve mount/root and effective access before use. */
@Serializable
data class IncomingSharedItem(
    val id: String,
    val remoteItem: SharedRemoteItem,
    val name: String? = null,
    val folder: JsonObject? = null,
    val file: JsonObject? = null,
    @SerialName("@client.synchronize") val synchronized: Boolean? = null,
    @SerialName("@libre.graph.permissions.actions.allowedValues") val effectiveActions: Set<String>? = null,
)

@Serializable
data class SharedRemoteItem(
    val id: String,
    val name: String? = null,
    val folder: JsonObject? = null,
    val file: JsonObject? = null,
    val webDavUrl: String? = null,
    val eTag: String? = null,
    val parentReference: SharedParentReference? = null,
    val permissions: List<SharedItemGrant>? = null,
)

@Serializable
data class SharedParentReference(
    val driveId: String? = null,
    val id: String? = null,
)

/** Never union arbitrary grants to infer the caller's rights; retain recipient identities and role IDs. */
@Serializable
data class SharedItemGrant(
    val id: String? = null,
    val roles: Set<String> = emptySet(),
    @SerialName("@libre.graph.permissions.actions") val actions: Set<String> = emptySet(),
    val grantedToV2: SharedGrantRecipient? = null,
)

@Serializable
data class SharedGrantRecipient(
    val user: SharedGrantIdentity? = null,
    val group: SharedGrantIdentity? = null,
)

@Serializable
data class SharedGrantIdentity(
    val id: String,
)
