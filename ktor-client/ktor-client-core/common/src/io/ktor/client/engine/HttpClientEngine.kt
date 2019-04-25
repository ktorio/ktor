package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.io.core.*

/**
 * Base interface use to define engines for [HttpClient].
 */
interface HttpClientEngine : CoroutineScope, Closeable {

    /**
     * [CoroutineDispatcher] specified for io operations.
     */
    val dispatcher: CoroutineDispatcher

    /**
     * Engine configuration
     */
    val config: HttpClientEngineConfig

    /**
     * Creates a new [HttpClientCall] specific for this engine, using a request [data].
     */
    @InternalAPI
    suspend fun execute(data: HttpRequestData): HttpResponseData

    /**
     * Install engine into [HttpClient].
     */
    @InternalAPI
    fun install(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Engine) { content ->
            val requestData = HttpRequestBuilder().apply {
                takeFrom(context)
                body = content
            }.build()

            validateHeaders(requestData)

            val responseData = execute(requestData)
            val call = HttpClientCall(client, requestData, responseData)

            responseData.callContext[Job]!!.invokeOnCompletion { cause ->
                @Suppress("UNCHECKED_CAST")
                val childContext = requestData.executionContext as CompletableJob
                if (cause == null) childContext.complete() else childContext.completeExceptionally(cause)
            }

            proceedWith(call)
        }
    }
}


/**
 * Factory of [HttpClientEngine] with a specific [T] of [HttpClientEngineConfig].
 */
interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    /**
     * Creates a new [HttpClientEngine] optionally specifying a [block] configuring [T].
     */
    fun create(block: T.() -> Unit = {}): HttpClientEngine
}

/**
 * Creates a new [HttpClientEngineFactory] based on this one
 * with further configurations from the [nested] block.
 */
fun <T : HttpClientEngineConfig> HttpClientEngineFactory<T>.config(nested: T.() -> Unit): HttpClientEngineFactory<T> {
    val parent = this

    return object : HttpClientEngineFactory<T> {
        override fun create(block: T.() -> Unit): HttpClientEngine = parent.create {
            nested()
            block()
        }
    }
}

/**
 * Validates request headers and fails if there are unsafe headers supplied
 */
private fun validateHeaders(request: HttpRequestData) {
    val requestHeaders = request.headers
    for (header in HttpHeaders.UnsafeHeaders) {
        if (header in requestHeaders) {
            throw UnsafeHeaderException(header)
        }
    }
}
