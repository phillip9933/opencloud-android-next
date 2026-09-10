package eu.opencloud.android.next.core.network

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
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

class RemoteDiscoveryClient(
    private val client: OkHttpClient,
) {
    @Suppress("TooGenericExceptionCaught")
    fun folder(
        rootWebDavUrl: String,
        path: String,
        authorization: String,
    ): List<RemoteResource> {
        val url = rootWebDavUrl.childUrl(path)
        val request =
            Request
                .Builder()
                .url(
                    url,
                ).header("Authorization", authorization)
                .header("Depth", "1")
                .method("PROPFIND", PROPFIND_BODY)
                .build()
        val body = execute(request)
        val document =
            try {
                secureDocumentBuilderFactory().newDocumentBuilder().parse(
                    ByteArrayInputStream(body.toByteArray()),
                )
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "WebDAV XML parsing failed for $url", exception)
                throw exception
            }
        val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
        return (0 until responses.length).mapNotNull { index ->
            val response = responses.item(index) as Element
            val href = response.text(DAV_NAMESPACE, "href") ?: return@mapNotNull null
            if (sameCollection(url, href)) return@mapNotNull null
            val decodedPath =
                URLDecoder
                    .decode(href.substringBefore('?'), StandardCharsets.UTF_8.name())
                    .trimEnd('/')
            val name =
                response.text(OC_NAMESPACE, "name")?.takeIf(String::isNotBlank) ?: decodedPath.substringAfterLast('/')
            val id = response.text(OC_NAMESPACE, "fileid") ?: response.text(OC_NAMESPACE, "id") ?: href
            RemoteResource(
                id = id,
                path = "${path.trimEnd('/')}/$name".let { if (it.startsWith('/')) it else "/$it" },
                name = name,
                folder = response.getElementsByTagNameNS(DAV_NAMESPACE, "collection").length > 0,
                mimeType = response.text(DAV_NAMESPACE, "getcontenttype"),
                size =
                    response.text(DAV_NAMESPACE, "getcontentlength")?.toLongOrNull()
                        ?: response.text(OC_NAMESPACE, "size")?.toLongOrNull()
                        ?: 0,
                eTag = response.text(DAV_NAMESPACE, "getetag"),
                modifiedAtEpochMillis = response.text(DAV_NAMESPACE, "getlastmodified").toEpochMillis(),
                createdAtEpochMillis = response.text(DAV_NAMESPACE, "creationdate").toEpochMillis(),
                favorite =
                    response.text(OC_NAMESPACE, "favorite") == "1" || response.text(OC_NAMESPACE, "favorite") == "true",
            )
        }
    }

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val excerpt = body.singleLineExcerpt()
                Log.e(
                    LOG_TAG,
                    "${request.method} ${request.url} failed with HTTP ${response.code}" +
                        excerpt.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(),
                )
                throw TransferHttpException(response.code, excerpt)
            }
            body
        }

    private companion object {
        const val DAV_NAMESPACE = "DAV:"
        const val OC_NAMESPACE = "http://owncloud.org/ns"
        const val LOG_TAG = "OpenCloudSync"
        val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:prop><oc:fileid/><oc:id/><oc:name/><oc:size/><oc:favorite/><d:getetag/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/><d:creationdate/><d:resourcetype/></d:prop></d:propfind>"""
                .toRequestBody(
                    "application/xml".toMediaType(),
                )
    }
}

private fun String.singleLineExcerpt(): String = replace(Regex("\\s+"), " ").trim().take(512)

data class RemoteResource(
    val id: String,
    val path: String,
    val name: String,
    val folder: Boolean,
    val mimeType: String?,
    val size: Long,
    val eTag: String?,
    val modifiedAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val favorite: Boolean = false,
)

private fun Element.text(
    namespace: String,
    localName: String,
): String? = getElementsByTagNameNS(namespace, localName).item(0)?.textContent

private fun String?.toEpochMillis(): Long =
    runCatching { this?.let(ZonedDateTime::parse)?.toInstant()?.toEpochMilli() }.getOrNull() ?: 0

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

private fun sameCollection(
    requestUrl: String,
    href: String,
): Boolean {
    val responsePath = runCatching { href.toHttpUrl().encodedPath }.getOrElse { href.substringBefore('?') }
    return requestUrl.toHttpUrl().encodedPath.trimEnd('/') == responsePath.trimEnd('/')
}

private fun secureDocumentBuilderFactory() =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }
