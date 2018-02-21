package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.io.*


class HttpClient private constructor(
        private val engine: HttpClientEngine,
        block: suspend HttpClientConfig.() -> Unit = {}
) : Closeable {

    constructor(
            engineFactory: HttpClientEngineFactory<*>,
            block: suspend HttpClientConfig.() -> Unit = {}
    ) : this(engineFactory.create(), block)

    val requestPipeline = HttpRequestPipeline()
    val responsePipeline = HttpResponsePipeline()
    val attributes = Attributes()

    private val config: HttpClientConfig = HttpClientConfig()

    init {
        runBlocking {
            config.block()
            config.install(HttpPlainText)
            config.install(HttpIgnoreBody)
        }

        config.install(this)
    }

    fun createRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            engine.prepareRequest(builder, call)

    fun config(block: suspend HttpClientConfig.() -> Unit): HttpClient = HttpClient(engine, block)

    override fun close() {
        engine.close()
    }
}
