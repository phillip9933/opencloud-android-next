package eu.opencloud.android.next.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.w3c.dom.Document
import org.w3c.dom.Element

/** A failed resource response is not evidence that the resource was deleted. */
internal fun validateFolderSnapshot(
    document: Document,
    requestUrl: String,
) {
    val root = document.documentElement
    if (root.namespaceURI != DAV || root.localName != "multistatus") invalidSnapshot()
    val requested = requestUrl.toHttpUrl()
    val parentSegments = requested.pathSegments.dropLastWhile { it.isEmpty() }
    var foundCollection = false
    val seen = mutableSetOf<String>()
    val responses = root.getElementsByTagNameNS(DAV, "response")
    for (index in 0 until responses.length) {
        val response = responses.item(index) as Element
        val href = response.getElementsByTagNameNS(DAV, "href").item(0)?.textContent ?: invalidSnapshot()
        val url = requested.resolve(href) ?: invalidSnapshot()
        requireSameOrigin(url, requested)
        val segments = url.pathSegments.dropLastWhile { it.isEmpty() }
        if (segments != parentSegments && segments.dropLast(1) != parentSegments) invalidSnapshot()
        if (!seen.add(segments.joinToString("/"))) invalidSnapshot()
        keepSuccessfulProperties(response)
        if (segments == parentSegments) {
            if (response.getElementsByTagNameNS(DAV, "collection").length == 0) invalidSnapshot()
            foundCollection = true
        }
    }
    if (!foundCollection) invalidSnapshot()
}

private fun requireSameOrigin(
    url: okhttp3.HttpUrl,
    requested: okhttp3.HttpUrl,
) {
    if (url.scheme != requested.scheme || url.host != requested.host || url.port != requested.port) invalidSnapshot()
}

private fun keepSuccessfulProperties(response: Element) {
    val propstats = response.getElementsByTagNameNS(DAV, "propstat")
    var successful = false
    for (index in propstats.length - 1 downTo 0) {
        val propstat = propstats.item(index) as Element
        val status = propstat.getElementsByTagNameNS(DAV, "status").item(0)?.textContent
        val code =
            status
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toIntOrNull()
        if (code == 200) {
            successful = true
        } else {
            propstat.parentNode.removeChild(propstat)
        }
    }
    if (!successful) invalidSnapshot()
}

private fun invalidSnapshot(): Nothing = throw OpenCloudException(OpenCloudError.InvalidResponse)

private const val DAV = "DAV:"
