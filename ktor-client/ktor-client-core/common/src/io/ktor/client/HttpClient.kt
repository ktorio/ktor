package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.core.*
import kotlin.coroutines.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the dependencies.
 * https://ktor.io/clients/http-client/engines.html
 */
@HttpClientDsl
expect fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit = {}
): HttpClient

/**
 * Constructs an asynchronous [HttpClient] using the specified [engineFactory]
 * and an optional [block] for configuring this client.
 */
@HttpClientDsl
fun <T : HttpClientEngineConfig> HttpClient(
    engineFactory: HttpClientEngineFactory<T>,
    block: HttpClientConfig<T>.() -> Unit = {}
): HttpClient {
    val config: HttpClientConfig<T> = HttpClientConfig<T>().apply(block)
    val engine = engineFactory.create(config.engineConfig)

    return HttpClient(engine, config)
}

/**
 * Constructs an asynchronous [HttpClient] using the specified [engine]
 * and a [block] for configuring this client.
 */
@HttpClientDsl
fun HttpClient(
    engine: HttpClientEngine,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(engine, HttpClientConfig<HttpClientEngineConfig>().apply(block))

/**
 * Asynchronous client to perform HTTP requests.
 *
 * This is a generic implementation that uses a specific engine [HttpClientEngine].
 * @property engine: [HttpClientEngine] for executing requests.
 */
class HttpClient(
    @InternalAPI val engine: HttpClientEngine,
    private val userConfig: HttpClientConfig<out HttpClientEngineConfig> = HttpClientConfig()
) : CoroutineScope, Closeable {
    private val closed = atomic(false)

    override val coroutineContext: CoroutineContext get() = engine.coroutineContext

    /**
     * Pipeline used for processing all the requests sent by this client.
     */
    val requestPipeline: HttpRequestPipeline = HttpRequestPipeline()

    /**
     * Pipeline used for processing all the responses sent by the server.
     */
    val responsePipeline: HttpResponsePipeline = HttpResponsePipeline()

    /**
     * Pipeline used for sending the request
     */
    val sendPipeline: HttpSendPipeline = HttpSendPipeline()

    /**
     * Pipeline used for receiving request
     */
    val receivePipeline: HttpReceivePipeline = HttpReceivePipeline()

    /**
     * Typed attributes used as a lightweight container for this client.
     */
    val attributes: Attributes = Attributes(concurrent = true)

    /**
     * Dispatcher handles io operations
     */
    @Deprecated(
        "[dispatcher] is deprecated. Use coroutineContext instead.",
        replaceWith = ReplaceWith("coroutineContext"),
        level = DeprecationLevel.ERROR
    )
    val dispatcher: CoroutineDispatcher
        get() = engine.dispatcher

    /**
     * Client engine config
     */
    val engineConfig: HttpClientEngineConfig = engine.config

    internal val config = HttpClientConfig<HttpClientEngineConfig>()

    init {
        engine.install(this)

        sendPipeline.intercept(HttpSendPipeline.Receive) { call ->
            check(call is HttpClientCall)
            val receivedCall = receivePipeline.execute(call, call.response).call
            proceedWith(receivedCall)
        }

        with(userConfig) {
            if (useDefaultTransformers) {
                config.install(HttpPlainText)
                config.install("DefaultTransformers") { defaultTransformers() }
            }

            if (expectSuccess) config.addDefaultResponseValidation()

            config.install(HttpSend)

            if (followRedirects) config.install(HttpRedirect)

            config += this
            config.install(this@HttpClient)
        }
    }

    /**
     * Creates a new [HttpRequest] from a request [data] and a specific client [call].
     */
    suspend fun execute(builder: HttpRequestBuilder): HttpClientCall =
        requestPipeline.execute(builder, builder.body) as HttpClientCall

    /**
     * Returns a new [HttpClient] copying this client configuration,
     * and additionally configured by the [block] parameter.
     */
    fun config(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(
        engine, HttpClientConfig<HttpClientEngineConfig>().apply {
            plusAssign(userConfig)
            block()
        }
    )

    /**
     * Closes the underlying [engine].
     */
    override fun close() {
        val success = closed.compareAndSet(false, true)
        if (!success) return

        attributes.allKeys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val feature = attributes[key as AttributeKey<Any>]

            if (feature is Closeable) {
                feature.close()
            }
        }

        engine.close()
    }

}

