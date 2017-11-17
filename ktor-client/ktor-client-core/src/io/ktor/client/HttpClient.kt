package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.util.*
import java.io.*


sealed class HttpClient : Closeable {
    abstract val attributes: Attributes
    abstract val requestPipeline: HttpRequestPipeline
    abstract val responsePipeline: HttpResponsePipeline

    companion object {
        operator fun invoke(
                backendFactory: HttpClientEngineFactory<*>,
                block: ClientConfig.() -> Unit = {}
        ) = HttpClientFactory.createDefault(backendFactory, block)
    }
}

class HttpCallScope(private val parent: Closeable) : HttpClient() {
    override val attributes = Attributes()
    override val requestPipeline: HttpRequestPipeline = HttpRequestPipeline()
    override val responsePipeline: HttpResponsePipeline = HttpResponsePipeline()

    override fun close() {
        parent.close()
    }
}
