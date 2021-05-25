/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@SharedImmutable
internal val CALL_COROUTINE = CoroutineName("call-context")

/**
 * Base interface use to define engines for [HttpClient].
 */
public interface HttpClientEngine : CoroutineScope, Closeable {
    /**
     * [CoroutineDispatcher] specified for io operations.
     */
    public val dispatcher: CoroutineDispatcher

    /**
     * Engine configuration
     */
    public val config: HttpClientEngineConfig

    /**
     * Set of supported engine extensions.
     */
    public val supportedCapabilities: Set<HttpClientEngineCapability<*>>
        get() = emptySet()

    private val closed: Boolean
        get() = !(coroutineContext[Job]?.isActive ?: false)

    /**
     * Creates a new [HttpClientCall] specific for this engine, using a request [data].
     */
    @InternalAPI
    public suspend fun execute(data: HttpRequestData): HttpResponseData

    /**
     * Install engine into [HttpClient].
     */
    @InternalAPI
    public fun install(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Engine) { content ->
            val requestData = HttpRequestBuilder().apply {
                takeFromWithExecutionContext(context)
                body = content
            }.build()

            validateHeaders(requestData)
            checkExtensions(requestData)

            val responseData = executeWithinCallContext(requestData)
            val call = HttpClientCall(client, requestData, responseData)

            proceedWith(call)
        }
    }

    /**
     * Create call context and use it as a coroutine context to [execute] request.
     */
    private suspend fun executeWithinCallContext(requestData: HttpRequestData): HttpResponseData {
        val callContext = createCallContext(requestData.executionContext)
        callContext.makeShared()

        val context = callContext + KtorCallContextElement(callContext)
        return async(context) {
            if (closed) {
                throw ClientEngineClosedException()
            }

            execute(requestData)
        }.await()
    }

    private fun checkExtensions(requestData: HttpRequestData) {
        for (requestedExtension in requestData.requiredCapabilities) {
            require(supportedCapabilities.contains(requestedExtension)) { "Engine doesn't support $requestedExtension" }
        }
    }
}

/**
 * Factory of [HttpClientEngine] with a specific [T] of [HttpClientEngineConfig].
 */
public interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    /**
     * Creates a new [HttpClientEngine] optionally specifying a [block] configuring [T].
     */
    public fun create(block: T.() -> Unit = {}): HttpClientEngine
}

/**
 * Creates a new [HttpClientEngineFactory] based on this one
 * with further configurations from the [nested] block.
 */
public fun <T : HttpClientEngineConfig> HttpClientEngineFactory<T>.config(
    nested: T.() -> Unit
): HttpClientEngineFactory<T> {
    val parent = this

    return object : HttpClientEngineFactory<T> {
        override fun create(block: T.() -> Unit): HttpClientEngine = parent.create {
            nested()
            block()
        }
    }
}

/**
 * Create call context with the specified [parentJob] to be used during call execution in the engine. Call context
 * inherits [coroutineContext], but overrides job and coroutine name so that call job's parent is [parentJob] and
 * call coroutine's name is "call-context".
 */
internal expect suspend fun HttpClientEngine.createCallContext(parentJob: Job): CoroutineContext

/**
 * Validates request headers and fails if there are unsafe headers supplied
 */
private fun validateHeaders(request: HttpRequestData) {
    val requestHeaders = request.headers
    val unsafeRequestHeaders = requestHeaders.names().filter {
        it in HttpHeaders.UnsafeHeadersList
    }
    if (unsafeRequestHeaders.isNotEmpty()) {
        throw UnsafeHeaderException(unsafeRequestHeaders.toString())
    }
}
