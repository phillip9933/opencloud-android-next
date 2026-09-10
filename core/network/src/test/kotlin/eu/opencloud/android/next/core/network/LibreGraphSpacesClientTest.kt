package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LibreGraphSpacesClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LibreGraphSpacesClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = LibreGraphSpacesClient(OkHttpClient(), initiatorId = "test-initiator")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `list spaces parses oCIS drive metadata`() {
        server.enqueue(
            MockResponse().setBody(
                """{"value":[{"id":"drive","driveAlias":"project-mars","name":"Mars","driveType":"project","description":"Mission files","webUrl":"https://cloud.example.test/f/drive","lastModifiedDateTime":"2026-09-09T12:00:00Z","owner":{"user":{"id":"alice","displayName":"Alice"}},"quota":{"total":100,"used":40,"remaining":60,"state":"normal"},"root":{"id":"root","webDavUrl":"${server.url(
                    "dav/spaces/drive",
                )}","eTag":"tag"}}]}""",
            ),
        )

        val result = client.listSpaces(server.url("/").toString(), "Bearer token").single()

        assertEquals("drive", result.id)
        assertEquals("project-mars", result.driveAlias)
        assertEquals("Alice", result.ownerName)
        assertEquals(100L, result.quotaTotalBytes)
        assertEquals(40L, result.quotaUsedBytes)
        assertEquals(60L, result.quotaRemainingBytes)
        val request = server.takeRequest()
        assertEquals("/graph/v1.0/me/drives", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("test-initiator", request.getHeader("Initiator-ID"))
    }

    @Test
    fun `list spaces follows graph pagination`() {
        server.enqueue(
            MockResponse().setBody(
                """{"value":[${drive("one")}],"@odata.nextLink":"${server.url("graph/v1.0/me/drives?page=2")}"}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"value":[${drive("two")}]}"""))

        assertEquals(listOf("one", "two"), client.listSpaces(server.url("/").toString(), "Bearer token").map { it.id })
        assertEquals("/graph/v1.0/me/drives", server.takeRequest().path)
        assertEquals("/graph/v1.0/me/drives?page=2", server.takeRequest().path)
    }

    private fun drive(id: String): String =
        """{"id":"$id","name":"$id","driveType":"project","root":{"id":"$id-root","webDavUrl":"${server.url(
            "dav/spaces/$id",
        )}"}}"""
}
