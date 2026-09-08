package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteSearchClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RemoteSearchClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = RemoteSearchClient(OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `search sends search-files report and parses resources`() {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:response><d:href>/dav/spaces/space/Documents/Plan.pdf</d:href><d:propstat><d:prop><oc:fileid>plan</oc:fileid><oc:name>Plan.pdf</oc:name><d:getcontenttype>application/pdf</d:getcontenttype><d:getcontentlength>42</d:getcontentlength><d:getetag>tag</d:getetag></d:prop></d:propstat></d:response></d:multistatus>""",
            ),
        )

        val result = client.search(server.url("dav/spaces/").toString(), "plan & notes", "Bearer token").single()

        assertEquals("space", result.spaceId)
        assertEquals("/Documents/Plan.pdf", result.path)
        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("<oc:pattern>plan &amp; notes</oc:pattern>"))
    }
}
