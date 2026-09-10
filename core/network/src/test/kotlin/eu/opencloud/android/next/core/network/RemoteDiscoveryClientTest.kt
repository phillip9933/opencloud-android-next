package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RemoteDiscoveryClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RemoteDiscoveryClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = RemoteDiscoveryClient(OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `folder parses depth one multistatus and excludes collection itself`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(
                    207,
                ).setBody(
                    """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:response><d:href>${server.url(
                        "dav/spaces/drive/",
                    )}</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response><d:response><d:href>/dav/spaces/drive/Photo.jpg</d:href><d:propstat><d:prop><oc:fileid>photo</oc:fileid><oc:name>Photo.jpg</oc:name><d:getcontenttype>image/jpeg</d:getcontenttype><d:getcontentlength>12</d:getcontentlength><d:getetag>etag</d:getetag></d:prop></d:propstat></d:response></d:multistatus>""",
                ),
        )
        val result = client.folder(server.url("dav/spaces/drive").toString(), "/", "Basic value").single()
        assertEquals("photo", result.id)
        assertEquals("/Photo.jpg", result.path)
        assertEquals("1", server.takeRequest().getHeader("Depth"))
    }
}
