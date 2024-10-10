/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.events.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Creates an asynchronous [HttpClient] with the specified [block] configuration.
 *
 * Note that the client requires an engine for processing network requests.
 * The [HttpClientEngine] is selected from the [dependencies](https://ktor.io/docs/client-dependencies.html).
 */
@KtorDsl
public expect fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit = {}
): HttpClient

/**
 * Creates an asynchronous [HttpClient] with the specified [HttpClientEngineFactory] and optional [block] configuration.
 * Note that a specific platform may require a specific engine for processing requests.
 * You can learn more about available engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
@KtorDsl
public fun <T : HttpClientEngineConfig> HttpClient(
    engineFactory: HttpClientEngineFactory<T>,
    block: HttpClientConfig<T>.() -> Unit = {}
): HttpClient {
    val config: HttpClientConfig<T> = HttpClientConfig<T>().apply(block)
    val engine = engineFactory.create(config.engineConfig)
    val client = HttpClient(engine, config, manageEngine = true)

    // If the engine was created using factory Ktor is responsible for its lifecycle management. Otherwise user has to
    // close engine by themself.
    client.coroutineContext[Job]!!.invokeOnCompletion {
        engine.close()
    }

    return client
}

/**
 * Creates an asynchronous [HttpClient] with the specified [HttpClientEngine] and optional [block] configuration.
 * Note that a specific platform may require a specific engine for processing requests.
 * You can learn more about available engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
@KtorDsl
public fun HttpClient(
    engine: HttpClientEngine,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(engine, HttpClientConfig<HttpClientEngineConfig>().apply(block), manageEngine = false)

/**
 * A multiplatform asynchronous HTTP client that allows you to make requests, handle responses,
 * and extend its functionality with plugins such as authentication, JSON serialization, and more.
 *
 * You can learn how to create and configure an [HttpClient] from
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 *
 * # Making API Requests
 * For every HTTP method (GET, POST, PUT, etc.), there is a corresponding function:
 * ```kotlin
 * val response: HttpResponse = client.get("https://ktor.io/")
 * val body = response.bodyAsText()
 * ```
 * See [Making HTTP requests](https://ktor.io/docs/client-requests.html) for more details.
 *
 * # Query Parameters
 * Add query parameters to your request using the `parameter` function:
 * ```kotlin
 * val response = client.get("https://google.com/search") {
 *     url {
 *         parameter("q", "REST API with Ktor")
 *     }
 * }
 * ```
 * For more information, refer to [Passing request parameters](https://ktor.io/docs/client-requests.html#parameters).
 *
 * # Adding Headers
 * Include headers in your request using the `headers` builder or the `header` function:
 * ```kotlin
 * val response = client.get("https://api.example.com/data") {
 *     headers {
 *         append("Authorization", "Bearer your_token_here")
 *         append("Accept", "application/json")
 *     }
 * }
 * ```
 * Learn more at [Adding headers to a request](https://ktor.io/docs/client-requests.html#headers).
 *
 * # JSON Serialization
 * Send and receive JSON data by installing the `ContentNegotiation` plugin with `kotlinx.serialization`:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 *
 * val response: MyResponseType = client.post("https://api.example.com/data") {
 *     contentType(ContentType.Application.Json)
 *     setBody(MyRequestType(someData = "value"))
 * }.body()
 * ```
 * See [Serializing JSON data](https://ktor.io/docs/client-serialization.html) for details.
 *
 * # Submitting Forms
 * Submit form data using `FormDataContent` or the `submitForm` function:
 * ```kotlin
 * // Using FormDataContent
 * val response = client.post("https://example.com/submit") {
 *     setBody(FormDataContent(Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }))
 * }
 *
 * // Or using submitForm
 * val response = client.submitForm(
 *     url = "https://example.com/submit",
 *     formParameters = Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }
 * )
 * ```
 * More information is available at [Submitting form parameters](https://ktor.io/docs/client-requests.html#form_parameters).
 *
 * # Handling Authentication
 * Use the `Auth` plugin to handle various authentication schemes like Basic or Bearer token authentication:
 * ```kotlin
 * val client = HttpClient {
 *     install(Auth) {
 *         bearer {
 *             loadTokens {
 *                 BearerTokens(accessToken = "your_access_token", refreshToken = "your_refresh_token")
 *             }
 *         }
 *     }
 * }
 *
 * val response = client.get("https://api.example.com/protected")
 * ```
 * Refer to [Client authentication](https://ktor.io/docs/client-auth.html) for more details.
 *
 * # Setting Timeouts and Retries
 * Configure timeouts and implement retry logic for your requests:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpTimeout) {
 *         requestTimeoutMillis = 10000
 *         connectTimeoutMillis = 5000
 *         socketTimeoutMillis = 15000
 *     }
 * }
 * ```
 * See [Timeout](https://ktor.io/docs/client-timeout.html) for more information.
 *
 * # Handling Cookies
 * Manage cookies automatically by installing the `HttpCookies` plugin:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpCookies) {
 *         storage = AcceptAllCookiesStorage()
 *     }
 * }
 *
 * // Accessing cookies
 * val cookies: List<Cookie> = client.cookies("https://example.com")
 * ```
 * Learn more at [Cookies](https://ktor.io/docs/client-cookies.html).
 *
 * # Uploading Files
 * Upload files using multipart/form-data requests:
 * ```kotlin
 * val fileBytes = File("path/to/file.txt").readBytes()
 *
 * val response = client.submitFormWithBinaryData(
 *     url = "https://example.com/upload",
 *     formData = formData {
 *         append("description", "File upload example")
 *         append("file", fileBytes, Headers.build {
 *             append(HttpHeaders.ContentType, "text/plain")
 *             append(HttpHeaders.ContentDisposition, "filename=\"file.txt\"")
 *         })
 *     }
 * )
 * ```
 * See [Uploading data](https://ktor.io/docs/client-requests.html#upload_file) for details.
 *
 * # Downloading Files
 * Download files and save them to the local file system:
 * ```kotlin
 * val response: ByteArray = client.get("https://example.com/file.zip").body()
 * File("downloaded_file.zip").writeBytes(response)
 * ```
 * Refer to [Receiving content](https://ktor.io/docs/client-responses.html).
 *
 * # Using WebSockets
 * Communicate over WebSockets using the `webSocket` function:
 * ```kotlin
 * client.webSocket("wss://echo.websocket.org") {
 *     send(Frame.Text("Hello, WebSocket!"))
 *     val frame = incoming.receive()
 *     if (frame is Frame.Text) {
 *         println("Received: ${frame.readText()}")
 *     }
 * }
 * ```
 * Learn more at [Client WebSockets](https://ktor.io/docs/client-websockets.html).
 *
 * # Error Handling
 * Handle exceptions and HTTP error responses gracefully:
 * ```kotlin
 * try {
 *     val response = client.get("https://api.example.com/data")
 *     if (response.status.isSuccess()) {
 *         // Process successful response
 *     } else {
 *         println("HTTP error: ${response.status}")
 *     }
 * } catch (e: ClientRequestException) {
 *     // Handle 4xx client errors
 *     println("Client error: ${e.response.status}")
 * } catch (e: ServerResponseException) {
 *     // Handle 5xx server errors
 *     println("Server error: ${e.response.status}")
 * } catch (e: Exception) {
 *     // Handle other exceptions
 *     println("Unexpected error: ${e.message}")
 * }
 * ```
 * See [Error handling](https://ktor.io/docs/client-response-validation.html) for more information.
 *
 * # Configuring SSL/TLS
 * Customize SSL/TLS settings for secure connections is engine-specific. Please refer to the following page for
 * the details: [Client SSL/TLS](https://ktor.io/docs/client-ssl.html).
 *
 * # Using Proxies
 * Route requests through an HTTP or SOCKS proxy:
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     engine {
 *         proxy = ProxyBuilder.http("http://proxy.example.com:8080")
 *         // For a SOCKS proxy:
 *         // proxy = ProxyBuilder.socks(host = "proxy.example.com", port = 1080)
 *     }
 * }
 * ```
 * See [Using a proxy](https://ktor.io/docs/client-proxy.html) for details.
 *
 * # Streaming Data
 * Stream large data efficiently without loading it entirely into memory:
 * ```kotlin
 * // Streaming a large file upload
 * val response = client.post("https://example.com/upload") {
 *     setBody(ChannelProvider {
 *         val file = File("path/to/largefile.zip").readChannel()
 *         file
 *     })
 * }
 *
 * // Streaming a large file download
 * client.get("https://example.com/largefile.zip")
 *     .bodyAsChannel()
 *     .copyAndClose(File("downloaded_largefile.zip").writeChannel())
 * ```
 * Learn more at [Streaming data](https://ktor.io/docs/client-responses.html#streaming).
 */
@OptIn(InternalAPI::class)
public class HttpClient(
    public val engine: HttpClientEngine,
    private val userConfig: HttpClientConfig<out HttpClientEngineConfig> = HttpClientConfig()
) : CoroutineScope, Closeable {
    private var manageEngine: Boolean = false

    internal constructor(
        engine: HttpClientEngine,
        userConfig: HttpClientConfig<out HttpClientEngineConfig>,
        manageEngine: Boolean
    ) : this(engine, userConfig) {
        this.manageEngine = manageEngine
    }

    private suspend fun x() {
        get("") {
            url {
                parameter("key", "value")
            }
        }
    }

    private val closed = atomic(false)

    private val clientJob: CompletableJob = Job(engine.coroutineContext[Job])

    public override val coroutineContext: CoroutineContext = engine.coroutineContext + clientJob

    /**
     * A pipeline used for processing all requests sent by this client.
     */
    public val requestPipeline: HttpRequestPipeline = HttpRequestPipeline()

    /**
     * A pipeline used for processing all responses sent by the server.
     */
    public val responsePipeline: HttpResponsePipeline = HttpResponsePipeline()

    /**
     * A pipeline used for sending a request.
     */
    public val sendPipeline: HttpSendPipeline = HttpSendPipeline()

    /**
     * A pipeline used for receiving a request.
     */
    public val receivePipeline: HttpReceivePipeline = HttpReceivePipeline()

    /**
     * Typed attributes used as a lightweight container for this client.
     */
    public val attributes: Attributes = Attributes(concurrent = true)

    /**
     * Provides access to the client's engine configuration.
     */
    public val engineConfig: HttpClientEngineConfig = engine.config

    /**
     * Provides access to the events of the client's lifecycle.
     */
    public val monitor: Events = Events()

    internal val config = HttpClientConfig<HttpClientEngineConfig>()

    init {
        if (manageEngine) {
            clientJob.invokeOnCompletion {
                if (it != null) {
                    engine.cancel()
                }
            }
        }

        engine.install(this)

        sendPipeline.intercept(HttpSendPipeline.Receive) { call ->
            check(call is HttpClientCall) { "Error: HttpClientCall expected, but found $call(${call::class})." }
            val response = receivePipeline.execute(Unit, call.response)
            call.setResponse(response)
            proceedWith(call)
        }

        with(userConfig) {
            config.install(HttpRequestLifecycle)
            config.install(BodyProgress)
            config.install(SaveBodyPlugin)

            if (useDefaultTransformers) {
                config.install("DefaultTransformers") { defaultTransformers() }
            }

            config.install(HttpSend)
            config.install(HttpCallValidator)

            if (followRedirects) {
                config.install(HttpRedirect)
            }

            config += this

            if (useDefaultTransformers) {
                config.install(HttpPlainText)
            }

            config.addDefaultResponseValidation()

            config.install(this@HttpClient)
        }

        responsePipeline.intercept(HttpResponsePipeline.Receive) {
            try {
                proceed()
            } catch (cause: Throwable) {
                monitor.raise(HttpResponseReceiveFailed, HttpResponseReceiveFail(context.response, cause))
                throw cause
            }
        }
    }

    /**
     * Creates a new [HttpClientCall] from a request [builder].
     */
    internal suspend fun execute(builder: HttpRequestBuilder): HttpClientCall {
        monitor.raise(HttpRequestCreated, builder)

        return requestPipeline.execute(builder, builder.body) as HttpClientCall
    }

    /**
     * Checks if the specified [capability] is supported by this client.
     */
    public fun isSupported(capability: HttpClientEngineCapability<*>): Boolean {
        return engine.supportedCapabilities.contains(capability)
    }

    /**
     * Returns a new [HttpClient] by copying this client's configuration
     * and additionally configured by the [block] parameter.
     */
    public fun config(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(
        engine,
        HttpClientConfig<HttpClientEngineConfig>().apply {
            plusAssign(userConfig)
            block()
        },
        manageEngine
    )

    /**
     * Closes the underlying [engine].
     */
    override fun close() {
        val success = closed.compareAndSet(false, true)
        if (!success) return

        val installedFeatures = attributes[PLUGIN_INSTALLED_LIST]
        installedFeatures.allKeys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val plugin = installedFeatures[key as AttributeKey<Any>]

            if (plugin is Closeable) {
                plugin.close()
            }
        }

        clientJob.complete()
        if (manageEngine) {
            engine.close()
        }
    }

    override fun toString(): String = "HttpClient[$engine]"
}
