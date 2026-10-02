package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DavChecksumTest {
    @Test fun `parses combined checksums and ignores weak checksum algorithms`() {
        val result =
            parseDavObject(
                xml("<oc:checksums><oc:checksum>MD5:$MD5 ADLER32:062c0215</oc:checksum></oc:checksums>"),
                URL,
            )
        assertEquals(mapOf("MD5" to MD5), result.checksums)
        assertThrows(OpenCloudException::class.java) {
            parseDavObject(xml("<oc:checksums><oc:checksum>SHA256:bad</oc:checksum></oc:checksums>"), URL)
        }
    }

    @Test fun `destination without checksum uses conditional byte verification`() {
        MockWebServer().use { server ->
            server.start()
            val client = DavOperationClient(OkHttpClient())
            listOf("hello", "other").forEach { body ->
                server.enqueue(MockResponse().setResponseCode(207).setBody(xml("")))
                server.enqueue(MockResponse().setBody(body).setHeader("ETag", "\"v1\""))
                val matches =
                    client.matchesFingerprint(
                        server.url("/file").toString(),
                        "checksum-v1:MD5:5:$MD5",
                        "Bearer test",
                        {},
                    )
                if (body == "hello") assertTrue(matches) else assertFalse(matches)
                assertEquals("PROPFIND", server.takeRequest().method)
                val get = server.takeRequest()
                assertEquals("GET", get.method)
                assertEquals("\"v1\"", get.getHeader("If-Match"))
            }
        }
    }

    private fun xml(checksums: String) =
        """
        <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:response><d:href>/file</d:href>
        <d:propstat><d:prop><d:resourcetype/><d:getcontentlength>5</d:getcontentlength><d:getetag>"v1"</d:getetag>
        $checksums</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
        """.trimIndent()

    private companion object {
        const val MD5 = "5d41402abc4b2a76b9719d911017c592"
        const val URL = "https://cloud.example/file"
    }
}
