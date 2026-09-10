package eu.opencloud.android.next.core.network

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class LibreGraphSpacesClient(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val initiatorId: String = CLIENT_INITIATOR_ID,
) {
    fun listSpaces(
        serverUrl: String,
        authorization: String,
    ): List<RemoteSpace> {
        val spaces = mutableListOf<RemoteSpace>()
        var nextUrl: String? = drivesUrl(serverUrl)
        while (nextUrl != null) {
            val page = execute(nextUrl, authorization)
            spaces += page.spaces
            nextUrl = page.nextUrl
        }
        return spaces
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
                runCatching { Log.e(LOG_TAG, "GET ${request.url} failed with HTTP ${response.code}\n$body") }
                throw TransferHttpException(response.code, body.singleLineExcerpt())
            }
            val payload = json.parseToJsonElement(body).jsonObject
            SpacesPage(
                spaces = payload["value"]?.jsonArray.orEmpty().map { it.jsonObject.toRemoteSpace() },
                nextUrl = payload.string("@odata.nextLink"),
            )
        }
    }

    private companion object {
        const val LOG_TAG = "OpenCloudSync"
        val CLIENT_INITIATOR_ID = UUID.randomUUID().toString()
    }
}

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

private fun drivesUrl(serverUrl: String): String =
    serverUrl
        .toHttpUrl()
        .newBuilder()
        .addPathSegments("graph/v1.0/me/drives")
        .build()
        .toString()

private fun JsonObject.requiredString(name: String): String = string(name) ?: error("Missing required field: $name")

private fun JsonObject.requiredObject(name: String): JsonObject =
    get(name)?.jsonObject ?: error("Missing required object: $name")

private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.content

private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()

private fun String.singleLineExcerpt(): String = replace(Regex("\\s+"), " ").trim().take(512)
