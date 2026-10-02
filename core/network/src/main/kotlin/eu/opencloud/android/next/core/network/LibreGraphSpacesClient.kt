package eu.opencloud.android.next.core.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class LibreGraphSpacesClient(
    client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val initiatorId: String = CLIENT_INITIATOR_ID,
    private val endpoints: EndpointPolicy = EndpointPolicy(),
) {
    private val client =
        client
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    fun listSpaces(
        serverUrl: String,
        authorization: String,
    ): List<RemoteSpace> = snapshot(serverUrl, authorization).spaces

    /** Creates a project space through LibreGraph; persistence is confirmed by a subsequent snapshot. */
    fun createProjectSpace(
        serverUrl: String,
        authorization: String,
        name: String,
    ): String {
        require(name.isNotBlank())
        val url = drivesUrl(serverUrl, includeMe = false).let(endpoints::endpoint)
        val body = buildJsonObject { put("name", name.trim()) }.toString()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Initiator-ID", initiatorId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code != 201) {
                runCatching { Log.e(LOG_TAG, "POST failed with HTTP ${response.code}") }
                throw TransferHttpException(response.code)
            }
            val drive = json.parseToJsonElement(responseBody).jsonObject
            val id =
                drive.string("id")?.takeIf { it.isNotBlank() }
                    ?: throw OpenCloudException(OpenCloudError.Unsupported)
            id
        }
    }

    /** Exclusions are authoritative only when the entire paginated response succeeds. */
    fun snapshot(
        serverUrl: String,
        authorization: String,
    ): RemoteSpacesSnapshot {
        val spaces = mutableListOf<RemoteSpace>()
        val excluded = mutableSetOf<String>()
        val initial = endpoints.endpoint(drivesUrl(serverUrl))
        val visited = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        var nextUrl: String? = initial.toString()
        while (nextUrl != null) {
            val url = endpoints.endpoint(nextUrl)
            if (url.scheme != initial.scheme || url.host != initial.host || url.port != initial.port) {
                throw OpenCloudException(OpenCloudError.Trust)
            }
            if (!visited.add(url.toString()) || visited.size > 1000) {
                throw OpenCloudException(OpenCloudError.PreconditionFailed)
            }
            val page = execute(url.toString(), authorization)
            requireCompletePage(page.spaces.map { it.id } + page.excludedVaultIds, ids)
            spaces += page.spaces
            excluded += page.excludedVaultIds
            nextUrl =
                page.nextUrl?.let {
                    resolvePage(url, it)
                }
        }
        return RemoteSpacesSnapshot(spaces.toList(), excluded.toSet())
    }

    private fun execute(
        url: String,
        authorization: String,
    ): SpacesPage {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .header("Initiator-ID", initiatorId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .get()
                .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                runCatching { Log.e(LOG_TAG, "GET failed with HTTP ${response.code}") }
                throw TransferHttpException(response.code)
            }
            val payload = json.parseToJsonElement(body).jsonObject
            val records =
                (payload["value"] ?: throw OpenCloudException(OpenCloudError.Unsupported))
                    .jsonArray
                    .map { it.jsonObject }
            val (vaults, visible) = records.partition { it.string("@libre.graph.contentType") == VAULT_CONTENT_TYPE }
            SpacesPage(
                spaces = visible.map { it.toRemoteSpace() },
                excludedVaultIds = vaults.map { it.vaultId() },
                nextUrl = payload.string("@odata.nextLink"),
            )
        }
    }

    private companion object {
        const val LOG_TAG = "OpenCloudSync"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val CLIENT_INITIATOR_ID = UUID.randomUUID().toString()
    }
}

private fun resolvePage(
    base: okhttp3.HttpUrl,
    link: String,
): String = base.resolve(link)?.toString() ?: throw OpenCloudException(OpenCloudError.Trust)

private fun requireCompletePage(
    spaceIds: List<String>,
    ids: MutableSet<String>,
) {
    if (spaceIds.any { it.isBlank() || !ids.add(it) } || ids.size > 100_000) {
        throw OpenCloudException(OpenCloudError.PreconditionFailed)
    }
}

private fun JsonObject.vaultId(): String =
    string("id")?.takeIf { it.isNotBlank() } ?: throw OpenCloudException(OpenCloudError.Unsupported)

data class RemoteSpacesSnapshot(
    val spaces: List<RemoteSpace>,
    val excludedVaultIds: Set<String>,
)

data class RemoteSpace(
    val id: String,
    val name: String,
    val type: String,
    val description: String?,
    val driveAlias: String?,
    val webUrl: String?,
    val ownerId: String?,
    val ownerName: String?,
    val lastModifiedDateTime: String?,
    val rootId: String,
    val rootWebDavUrl: String,
    val rootETag: String?,
    val quotaTotalBytes: Long?,
    val quotaUsedBytes: Long?,
    val quotaRemainingBytes: Long?,
    val quotaState: String?,
    val disabled: Boolean,
    val deleted: Boolean,
)

private data class SpacesPage(
    val spaces: List<RemoteSpace>,
    val excludedVaultIds: List<String>,
    val nextUrl: String?,
)

private fun JsonObject.toRemoteSpace(): RemoteSpace {
    val root = requiredObject("root")
    val owner = get("owner")?.jsonObject?.get("user")?.jsonObject
    val quota = get("quota")?.jsonObject
    val type = string("driveType") ?: "project"
    return RemoteSpace(
        id = requiredString("id"),
        name = requiredString("name"),
        type = type,
        description = string("description"),
        driveAlias = string("driveAlias"),
        webUrl = string("webUrl"),
        ownerId = owner?.string("id"),
        ownerName = owner?.string("displayName"),
        lastModifiedDateTime = string("lastModifiedDateTime"),
        rootId = root.requiredString("id"),
        rootWebDavUrl = root.requiredString("webDavUrl"),
        rootETag = root.string("eTag"),
        quotaTotalBytes = quota?.long("total"),
        quotaUsedBytes = quota?.long("used"),
        quotaRemainingBytes = quota?.long("remaining"),
        quotaState = quota?.string("state"),
        disabled = type == "virtual",
        deleted = root["deleted"] != null,
    )
}

private fun drivesUrl(
    serverUrl: String,
    includeMe: Boolean = true,
): String =
    serverUrl
        .toHttpUrl()
        .newBuilder()
        .apply {
            addPathSegments(if (includeMe) "graph/v1.0/me/drives" else "graph/v1.0/drives")
        }.build()
        .toString()

private fun JsonObject.requiredString(name: String): String = string(name) ?: error("Missing required field: $name")

private fun JsonObject.requiredObject(name: String): JsonObject =
    get(name)?.jsonObject ?: error("Missing required object: $name")

private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.content

private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()
