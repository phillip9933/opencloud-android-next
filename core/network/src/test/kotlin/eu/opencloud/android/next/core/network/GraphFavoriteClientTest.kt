package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GraphFavoriteClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GraphFavoriteClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = GraphFavoriteClient(OkHttpClient(), TEST_INITIATOR_ID)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `adding favorite follows graph drive item`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.setFavorite(server.url("/").toString(), "item-id", "Bearer token", true)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/graph/v1.0/me/drive/items/item-id/follow", request.path)
        assertEquals(0L, request.bodySize)
        assertGraphHeaders(request)
    }

    @Test fun `removing favorite unfollows graph drive item`() {
        server.enqueue(MockResponse().setResponseCode(204))

        client.setFavorite(server.url("/").toString(), "item-id", "Bearer token", false)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/graph/v1.0/me/drive/following/item-id", request.path)
        assertGraphHeaders(request)
    }

    @Test fun `item id is encoded as one path segment`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.setFavorite(server.url("/").toString(), "storage\$id!/folder name", "Bearer token", true)

        assertEquals(
            "/graph/v1.0/me/drive/items/storage\$id!%2Ffolder%20name/follow",
            server.takeRequest().path,
        )
    }

    @Test fun `favorite failure redacts graph response body`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"favorite denied"}"""))

        val exception =
            assertThrows(TransferHttpException::class.java) {
                client.setFavorite(server.url("/").toString(), "item-id", "Bearer token", true)
            }

        assertEquals(403, exception.statusCode)
        assertEquals("Access was denied.", exception.message)
    }

    private fun assertGraphHeaders(request: okhttp3.mockwebserver.RecordedRequest) {
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals(TEST_INITIATOR_ID, request.getHeader("Initiator-ID"))
        assertEquals("XMLHttpRequest", request.getHeader("X-Requested-With"))
        assertTrue(request.getHeader("X-Request-ID").orEmpty().isNotBlank())
        assertNull(request.getHeader("OCS-APIREQUEST"))
    }

    private companion object {
        const val TEST_INITIATOR_ID = "android-client-instance"
    }
}
