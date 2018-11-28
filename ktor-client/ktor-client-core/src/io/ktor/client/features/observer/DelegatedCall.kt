package io.ktor.client.features.observer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import kotlinx.coroutines.io.*


/**
 * Wrap existing [HttpClientCall] with new [content].
 *
 * [shouldCloseOrigin] - specify should we close the origin call with closing the new one.
 */
fun HttpClientCall.wrapWithContent(
    content: ByteReadChannel,
    shouldCloseOrigin: Boolean
): HttpClientCall = HttpClientCall(client).apply {
    request = DelegatedRequest(this, this@wrapWithContent.request)
    response = DelegatedResponse(content, this, shouldCloseOrigin, this@wrapWithContent.response)
}

internal class DelegatedRequest(
    override val call: HttpClientCall,
    origin: HttpRequest
) : HttpRequest by origin

internal class DelegatedResponse(
    override val content: ByteReadChannel,
    override val call: HttpClientCall,
    private val shouldClose: Boolean,
    origin: HttpResponse
) : HttpResponse by origin {
    override fun close() {
        if (shouldClose) super.close()
    }
}
