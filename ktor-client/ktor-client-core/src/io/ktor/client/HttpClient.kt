package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.util.*
import java.io.*


class HttpClient private constructor(
        private val engine: HttpClientEngine,
        private val config: HttpClientConfig
) : Closeable {
    constructor(engineFactory: HttpClientEngineFactory<*>, block: HttpClientConfig.() -> Unit = {})
            : this(engineFactory.create(), HttpClientConfig().apply(block))

    val requestPipeline = HttpRequestPipeline()
    val responsePipeline = HttpResponsePipeline()
    val attributes = Attributes()

    init {
        config.install(HttpPlainText)
        config.install(HttpIgnoreBody)

        config.install(this)
    }

    fun createRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            engine.prepareRequest(builder, call)

    fun config(block: HttpClientConfig.() -> Unit): HttpClient = HttpClient(engine, config.clone().apply(block))

    override fun close() {
        engine.close()
    }
}
