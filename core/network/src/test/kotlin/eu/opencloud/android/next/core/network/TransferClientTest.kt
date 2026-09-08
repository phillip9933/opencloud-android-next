package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class TransferClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TransferClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = TransferClient(OkHttpClient())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `plain upload streams content with conflict precondition`() {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "etag-1"))
        val bytes = "hello".toByteArray()

        val eTag =
            client.upload(server.url("file.txt").toString(), "Bearer token", "text/plain", bytes.size.toLong(), false, {
                ByteArrayInputStream(bytes)
            }, {})

        assertEquals("etag-1", eTag)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("*", request.getHeader("If-None-Match"))
        assertEquals("hello", request.body.readUtf8())
    }

    @Test
    fun `download resumes with a range header`() {
        server.enqueue(MockResponse().setResponseCode(206).setBody("world"))
        var result = ""

        client.download(server.url("file.txt").toString(), "Basic credentials", 5) { input, _, resumed ->
            assertEquals(true, resumed)
            result = input.bufferedReader().readText()
        }

        assertEquals("world", result)
        assertEquals("bytes=5-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `tus create head and patch preserve resumable state`() {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Location", server.url("uploads/1")))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("Upload-Offset", "2"))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("Upload-Offset", "5"))

        val url = client.createTusUpload(server.url("uploads").toString(), "Bearer token", 5, "filename ZmlsZS50eHQ=")
        assertEquals(2, client.tusOffset(url, "Bearer token"))
        assertEquals(5, client.patchTus(url, "Bearer token", 2, 3, { ByteArrayInputStream("llo".toByteArray()) }, {}))
        server.takeRequest()
        server.takeRequest()
        assertEquals("llo", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `tus patch writes only the declared chunk length`() {
        server.enqueue(MockResponse().setResponseCode(204).setHeader("Upload-Offset", "3"))

        client.patchTus(
            server.url("uploads/1").toString(),
            "Bearer token",
            0,
            3,
            { ByteArrayInputStream("abcdef".toByteArray()) },
            {},
        )

        assertEquals("abc", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `precondition failure becomes a conflict`() {
        server.enqueue(MockResponse().setResponseCode(412))
        assertThrows(TransferConflictException::class.java) {
            client.upload(server.url("file.txt").toString(), "Bearer token", null, 0, false, {
                ByteArrayInputStream(byteArrayOf())
            }, {})
        }
    }

    @Test
    fun `create collection issues MKCOL and accepts an existing collection`() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(405))

        client.createCollection(server.url("Camera Uploads").toString(), "Bearer token")
        client.createCollection(server.url("Camera Uploads/2026").toString(), "Bearer token")

        val created = server.takeRequest()
        assertEquals("MKCOL", created.method)
        assertEquals("Bearer token", created.getHeader("Authorization"))
        assertEquals("MKCOL", server.takeRequest().method)
    }
}
