package io.ktor.client.engine.winhttp

import io.ktor.client.engine.winhttp.internal.*
import io.ktor.http.fullPath
import io.ktor.http.isSecure
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class WinHttpProcessor(
    override val coroutineContext: CoroutineContext,
    private val config: WinHttpClientEngineConfig
) : CoroutineScope {

    suspend fun executeRequest(request: WinHttpRequestData): WinHttpResponseData {
        val context = WinHttpContext()
        context.createSession()
        context.setTimeouts(
            config.resolveTimeout,
            config.connectTimeout,
            config.sendTimeout,
            config.receiveTimeout
        )

        context.createConnection(request.url.host, request.url.port)
        context.openRequest(
            request.method,
            request.url.fullPath,
            request.url.protocol.isSecure()
        )

        if (request.headers.isNotEmpty()) {
            context.addRequestHeaders(request.headers)
        }

        // Add request body
        request.content?.let { content ->
            context.addRequestBody(content)
        }

        return context.sendRequestAsync().await()

        //return deferred.await()
    }

    fun close() {
    }
}
