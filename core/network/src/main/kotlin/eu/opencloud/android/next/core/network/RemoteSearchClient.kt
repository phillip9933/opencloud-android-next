package eu.opencloud.android.next.core.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import javax.xml.parsers.DocumentBuilderFactory

class RemoteSearchClient(
    private val client: OkHttpClient,
) {
    fun search(
        endpointUrl: String,
        query: String,
        authorization: String,
        limit: Int = 50,
    ): List<RemoteSearchResource> {
        require(query.isNotBlank()) { "A search query is required." }
        val request =
            Request
                .Builder()
                .url(endpointUrl)
                .header("Authorization", authorization)
                .method("REPORT", searchBody(query, limit))
                .build()
        val body = execute(request)
        val document =
            secureSearchDocumentBuilderFactory().newDocumentBuilder().parse(
                ByteArrayInputStream(body.toByteArray()),
            )
        val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
        return (0 until responses.length)
            .mapNotNull { index ->
                val response = responses.item(index) as Element
                val href = response.searchText(DAV_NAMESPACE, "href") ?: return@mapNotNull null
                val decodedPath =
                    URLDecoder
                        .decode(
                            href.substringBefore('?'),
                            StandardCharsets.UTF_8.name(),
                        ).trimEnd('/')
                val name =
                    response.searchText(OC_NAMESPACE, "name")?.takeIf(String::isNotBlank)
                        ?: decodedPath.substringAfterLast('/')
                val id = response.searchText(OC_NAMESPACE, "fileid") ?: return@mapNotNull null
                val spaceAndPath = decodedPath.substringAfter("/spaces/", "")
                val spaceId = spaceAndPath.substringBefore('/').takeIf(String::isNotBlank) ?: return@mapNotNull null
                val path = "/${spaceAndPath.substringAfter('/', "").trimStart('/')}"
                RemoteSearchResource(
                    id = id,
                    spaceId = spaceId,
                    path = path,
                    name = name,
                    folder = response.getElementsByTagNameNS(DAV_NAMESPACE, "collection").length > 0,
                    mimeType = response.searchText(DAV_NAMESPACE, "getcontenttype"),
                    size =
                        response.searchText(DAV_NAMESPACE, "getcontentlength")?.toLongOrNull()
                            ?: response.searchText(OC_NAMESPACE, "size")?.toLongOrNull()
                            ?: 0,
                    eTag = response.searchText(DAV_NAMESPACE, "getetag"),
                    modifiedAtEpochMillis = response.searchText(DAV_NAMESPACE, "getlastmodified").searchEpochMillis(),
                )
            }.distinctBy(RemoteSearchResource::id)
    }

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(LOG_TAG, "REPORT ${request.url} failed with HTTP ${response.code}")
                throw TransferHttpException(response.code, body.replace(Regex("\\s+"), " ").trim().take(512))
            }
            body
        }

    private fun searchBody(
        query: String,
        limit: Int,
    ) =
        """<?xml version="1.0"?><oc:search-files xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:prop><oc:fileid/><oc:name/><oc:size/><d:getetag/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/><d:resourcetype/></d:prop><oc:search><oc:pattern>${query.xmlEscape()}</oc:pattern><oc:limit>$limit</oc:limit></oc:search></oc:search-files>"""
            .toRequestBody("application/xml".toMediaType())

    private companion object {
        const val DAV_NAMESPACE = "DAV:"
        const val OC_NAMESPACE = "http://owncloud.org/ns"
        const val LOG_TAG = "OpenCloudSearch"
    }
}

data class RemoteSearchResource(
    val id: String,
    val spaceId: String,
    val path: String,
    val name: String,
    val folder: Boolean,
    val mimeType: String?,
    val size: Long,
    val eTag: String?,
    val modifiedAtEpochMillis: Long,
)

private fun secureSearchDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory
        .newInstance()
        .apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

private fun Element.searchText(
    namespace: String,
    localName: String,
): String? = getElementsByTagNameNS(namespace, localName).item(0)?.textContent

private fun String?.searchEpochMillis(): Long =
    runCatching { this?.let(ZonedDateTime::parse)?.toInstant()?.toEpochMilli() }.getOrNull() ?: 0

private fun String.xmlEscape(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
