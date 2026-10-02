package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedFolderAccessClientTest {
    private lateinit var server: MockWebServer
    private val client = SharedFolderAccessClient(OkHttpClient(), EndpointPolicy(true))
    private val share =
        IncomingSharedItem(
            "share",
            SharedRemoteItem("item", parentReference = SharedParentReference("drive")),
            effectiveActions = setOf("libre.graph/driveItem/upload/create"),
        )

    @Before fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After fun stop() = server.shutdown()

    @Test fun `fresh effective access overrides cached access and requests the exact remote item`() {
        server.enqueue(MockResponse().setBody(metadata("""["libre.graph/driveItem/children/read"]""")))
        val result = resolve() as SharedFolderResolution.Resolved
        assertEquals(server.url("/dav/shared/").toString(), result.webDavUrl)
        assertTrue(result.access.canBrowse)
        assertFalse(result.access.canUpload)
        assertFalse(result.access.canReadContent)
        val request = server.takeRequest()
        assertEquals("/prefix/graph/v1beta1/drives/drive/items/item", request.requestUrl?.encodedPath)
        assertEquals("@libre.graph.permissions.actions.allowedValues", request.requestUrl?.queryParameter("\$select"))
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test fun `upload and create permissions remain independent and unknown actions grant nothing`() {
        val access = SharedFolderAccess(setOf("libre.graph/driveItem/upload/create", "unknown"))
        assertTrue(access.canUpload)
        assertFalse(access.canCreateFolder)
        assertFalse(access.canBrowse)
        assertFalse(access.canReadContent)
        assertTrue(SharedFolderAccess(setOf("libre.graph/driveItem/children/create")).canCreateFolder)
        server.enqueue(MockResponse().setBody(metadata("[]")))
        assertFalse((resolve() as SharedFolderResolution.Resolved).access.canBrowse)
    }

    @Test fun `missing identities roots and permissions do not use cached metadata or grants`() {
        assertEquals(
            SharedFolderResolution.Unresolved(SharedFolderMissing.IDENTITY),
            resolve(share.copy(remoteItem = share.remoteItem.copy(parentReference = null))),
        )
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setBody("""{"id":"item","folder":{}}"""))
        assertEquals(SharedFolderResolution.Unresolved(SharedFolderMissing.DAV_ROOT), resolve())
        server.enqueue(MockResponse().setBody(metadata("null")))
        assertEquals(SharedFolderResolution.Unresolved(SharedFolderMissing.EFFECTIVE_ACCESS), resolve())
    }

    @Test fun `revoked missing and deleted folders return unavailable without cached fallback`() {
        for (status in listOf(403, 404, 410)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("private error"))
            assertEquals(SharedFolderResolution.Unavailable, resolve())
        }
        server.enqueue(MockResponse().setBody("""{"id":"item","deleted":{}}"""))
        assertEquals(SharedFolderResolution.Unavailable, resolve())
        server.enqueue(MockResponse().setBody("""{"id":"item","file":{}}"""))
        assertEquals(SharedFolderResolution.Unresolved(SharedFolderMissing.FOLDER), resolve())
    }

    @Test fun `identity mismatch untrusted root and malformed payload fail safely`() {
        val bodies =
            listOf(
                """{"id":"wrong","folder":{}}""",
                """{"id":"item","parentReference":{"driveId":"wrong"},"folder":{}}""",
                metadata("[]").replace(server.url("/dav/shared/").toString(), "https://untrusted.example/dav/"),
                "private malformed response",
                "x".repeat(1024 * 1024 + 1),
            )
        bodies.forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            val error = assertThrows(OpenCloudException::class.java) { resolve() }
            assertNull(error.cause)
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun `redirects and rate limits preserve safe HTTP errors without credential forwarding`() {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/capture")))
            assertThrows(TransferHttpException::class.java) { resolve() }
            assertEquals(0, other.requestCount)
        }
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody("private"))
        val failure = assertThrows(TransferHttpException::class.java) { resolve() }
        assertFalse(failure.message.orEmpty().contains("private"))
        assertEquals(OpenCloudError.RateLimited(60), failure.error)
    }

    private fun resolve(item: IncomingSharedItem = share) =
        client.resolve(server.url("/prefix/").toString(), "Bearer token", item)

    private fun metadata(actions: String) =
        """
        {"id":"item","folder":{},"webDavUrl":"${server.url("/dav/shared/")}",
        "@libre.graph.permissions.actions.allowedValues":$actions}
        """.trimIndent()
}
