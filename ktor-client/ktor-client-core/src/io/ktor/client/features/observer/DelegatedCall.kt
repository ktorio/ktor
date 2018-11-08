package io.ktor.client.features.observer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import kotlinx.coroutines.io.*


internal fun DelegatedCall(
    content: ByteReadChannel,
    origin: HttpClientCall,
    scope: HttpClient,
    shouldClose: Boolean
): HttpClientCall = HttpClientCall(scope).apply {
    request = DelegatedRequest(this, origin.request)
    response = DelegatedResponse(content, this, shouldClose, origin.response)
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
