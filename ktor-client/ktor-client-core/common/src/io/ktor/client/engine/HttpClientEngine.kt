/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal val CALL_COROUTINE = CoroutineName("call-context")
internal val CLIENT_CONFIG = AttributeKey<HttpClientConfig<*>>("client-config")

/**
 * Serves as the base interface for an [HttpClient]'s engine.
 *
 * An `HttpClientEngine` represents the underlying network implementation that
 * performs HTTP requests and handles responses.
 * Developers can implement this interface to create custom engines for use with [HttpClient].
 *
 * This interface provides a set of properties and methods that define the
 * contract for configuring, executing, and managing HTTP requests within the engine.
 *
 * For a base implementation that handles common engine functionality, see [HttpClientEngineBase].
 */
public interface HttpClientEngine : CoroutineScope, Closeable {
    /**
     * Specifies the [CoroutineDispatcher] for I/O operations in the engine.
     *
     * This dispatcher is used for all network-related operations, such as
     * sending requests and receiving responses.
     * By default, it should be optimized for I/O tasks.
     *
     * Example:
     * ```kotlin
     * override val dispatcher: CoroutineDispatcher = Dispatchers.IO
     * ```
     */
    public val dispatcher: CoroutineDispatcher

    /**
     * Provides access to the engine's configuration via [HttpClientEngineConfig].
     *
     * The [config] object stores user-defined parameters or settings that control
     * how the engine operates. When creating a custom engine, this property
     * should return the specific configuration implementation.
     *
     * Example:
     * ```kotlin
     * override val config: HttpClientEngineConfig = CustomEngineConfig()
     * ```
     */
    public val config: HttpClientEngineConfig

    /**
     * Specifies the set of capabilities supported by this HTTP client engine.
     *
     * Capabilities provide a mechanism for plugins and other components to
     * determine whether the engine supports specific features such as timeouts,
     * WebSocket communication, HTTP/2, HTTP/3, or other advanced networking
     * capabilities. This allows seamless integration of features based on the
     * engine's functionality.
     *
     * Each capability is represented as an instance of [HttpClientEngineCapability],
     * which can carry additional metadata or configurations for the capability.
     *
     * Example:
     * ```kotlin
     * override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(
     *     WebSocketCapability,
     *     Http2Capability,
     *     TimeoutCapability
     * )
     * ```
     *
     * **Usage in Plugins**:
     * Plugins can check if the engine supports a specific capability before
     * applying behavior:
     * ```kotlin
     * if (engine.supportedCapabilities.contains(WebSocketCapability)) {
     *     // Configure WebSocket-specific settings
     * }
     * ```
     *
     * When implementing a custom engine, ensure this property accurately reflects
     * the engine's abilities to avoid unexpected plugin behavior or runtime errors.
     */
    public val supportedCapabilities: Set<HttpClientEngineCapability<*>>
        get() = emptySet()

    private val closed: Boolean
        get() = !(coroutineContext[Job]?.isActive ?: false)

    /**
     * Executes an HTTP request and produces an HTTP response.
     *
     * This function takes [HttpRequestData], which contains all details of the HTTP request,
     * and returns [HttpResponseData] with the server's response, including headers, status code, and body.
     *
     * @param data The [HttpRequestData] representing the request to be executed.
     * @return An [HttpResponseData] object containing the server's response.
     */
    @InternalAPI
    public suspend fun execute(data: HttpRequestData): HttpResponseData

    /**
     * Installs the engine into an [HttpClient].
     *
     * This method is called when the engine is being set up within an `HttpClient`.
     * Use it to register interceptors, validate configuration, or prepare the engine
     * for use with the client.
     *
     * @param client The [HttpClient] instance to which the engine is being installed.
     */
    @InternalAPI
    public fun install(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Engine) { content ->
            val builder = HttpRequestBuilder().apply {
                takeFromWithExecutionContext(context)
                setBody(content)
            }

            client.monitor.raise(HttpRequestIsReadyForSending, builder)

            val requestData = builder.build().apply {
                attributes.put(CLIENT_CONFIG, client.config)
            }

            validateHeaders(requestData)
            checkExtensions(requestData)

            val responseData = executeWithinCallContext(requestData)
            val call = HttpClientCall(client, requestData, responseData)

            val response = call.response
            client.monitor.raise(HttpResponseReceived, response)

            response.coroutineContext.job.invokeOnCompletion {
                if (it != null) {
                    client.monitor.raise(HttpResponseCancelled, response)
                }
            }

            proceedWith(call)
        }
    }

    /**
     * Creates a call context and uses it as a coroutine context to [execute] a request.
     */
    @OptIn(InternalAPI::class)
    private suspend fun executeWithinCallContext(requestData: HttpRequestData): HttpResponseData {
        val callContext = createCallContext(requestData.executionContext)

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
 * Creates a call context with the specified [parentJob] to be used during call execution in the engine. Call context
 * inherits [coroutineContext], but overrides job and coroutine name so that call job's parent is [parentJob] and
 * call coroutine's name is "call-context".
 */
internal suspend fun HttpClientEngine.createCallContext(parentJob: Job): CoroutineContext {
    val callJob = Job(parentJob)
    val callContext = coroutineContext + callJob + CALL_COROUTINE

    attachToUserJob(callJob)

    return callContext
}

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
