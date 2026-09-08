package eu.opencloud.android.next.core.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import java.io.InputStream

class TransferClient(
    private val client: OkHttpClient,
) {
    fun download(
        url: String,
        authorization: String,
        startOffset: Long,
        sink: (InputStream, Long?, Boolean) -> Unit,
    ): String? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .apply {
                    if (startOffset > 0) header("Range", "bytes=$startOffset-")
                }.get()
                .build()
        return client.newCall(request).execute().use { response ->
            requireSuccessful(response, setOf(200, 206))
            sink(
                requireNotNull(response.body).byteStream(),
                response.body?.contentLength()?.takeIf { it >= 0 },
                startOffset > 0 && response.code == 206,
            )
            response.header("ETag")
        }
    }

    @Suppress("LongParameterList")
    fun upload(
        url: String,
        authorization: String,
        mimeType: String?,
        length: Long,
        overwrite: Boolean,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): String? {
        val body = streamingBody(mimeType, length, source, onProgress)
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .apply {
                    if (!overwrite) header("If-None-Match", "*")
                }.put(body)
                .build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 412) throw TransferConflictException()
            requireSuccessful(response, setOf(200, 201, 204))
            response.header("ETag")
        }
    }

    fun createCollection(
        url: String,
        authorization: String,
    ) {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .method("MKCOL", EMPTY_BODY)
                .build()
        client.newCall(request).execute().use { response ->
            requireSuccessful(response, setOf(201, 405))
        }
    }

    fun createTusUpload(
        endpoint: String,
        authorization: String,
        length: Long,
        metadata: String,
    ): String {
        val request =
            Request
                .Builder()
                .url(endpoint)
                .header("Authorization", authorization)
                .header("Tus-Resumable", TUS_VERSION)
                .header("Upload-Length", length.toString())
                .header("Upload-Metadata", metadata)
                .post(EMPTY_BODY)
                .build()
        return client.newCall(request).execute().use { response ->
            requireSuccessful(response, setOf(201))
            requireNotNull(response.header("Location")) { "The TUS server did not return an upload location." }
        }
    }

    fun tusOffset(
        url: String,
        authorization: String,
    ): Long {
        val request =
            Request
                .Builder()
                .url(
                    url,
                ).header("Authorization", authorization)
                .header("Tus-Resumable", TUS_VERSION)
                .head()
                .build()
        return client.newCall(request).execute().use { response ->
            requireSuccessful(response, setOf(200, 204))
            response.header("Upload-Offset")?.toLongOrNull() ?: error("The TUS server returned no valid offset.")
        }
    }

    @Suppress("LongParameterList")
    fun patchTus(
        url: String,
        authorization: String,
        offset: Long,
        length: Long,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): Long {
        val body = streamingBody("application/offset+octet-stream", length, source, onProgress)
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", authorization)
                .header("Tus-Resumable", TUS_VERSION)
                .header("Upload-Offset", offset.toString())
                .patch(body)
                .build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 409) throw TusOffsetException()
            requireSuccessful(response, setOf(204))
            response.header("Upload-Offset")?.toLongOrNull() ?: error("The TUS server returned no valid offset.")
        }
    }

    private fun streamingBody(
        mimeType: String?,
        length: Long,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ) = object : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun contentLength() = length

        override fun writeTo(sink: BufferedSink) {
            source().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var written = 0L
                while (written < length) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), length - written).toInt())
                    if (count < 0) error("The transfer source ended before its declared length.")
                    sink.write(buffer, 0, count)
                    written += count
                    onProgress(written)
                }
            }
        }
    }

    private fun requireSuccessful(
        response: Response,
        expected: Set<Int>,
    ) {
        if (response.code !in expected) {
            val code = response.code
            response.closeQuietly()
            throw TransferHttpException(code)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val TUS_VERSION = "1.0.0"
        val EMPTY_BODY =
            object : RequestBody() {
                override fun contentType() = null

                override fun contentLength() = 0L

                override fun writeTo(sink: BufferedSink) = Unit
            }
    }
}

class TransferHttpException(
    val statusCode: Int,
    responseBody: String? = null,
) : IllegalStateException(
        buildString {
            append("HTTP $statusCode")
            responseBody?.takeIf(String::isNotBlank)?.let { append(": $it") }
        },
    )

class TransferConflictException : IllegalStateException("The destination already contains an item with this name.")

class TusOffsetException : IllegalStateException("The TUS upload offset changed on the server.")
