package io.ktor.client

import io.ktor.client.backend.HttpClientBackendFactory
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.response.HttpResponsePipeline
import io.ktor.util.Attributes
import java.io.Closeable


sealed class HttpClient : Closeable {
    abstract val attributes: Attributes

    abstract val requestPipeline: HttpRequestPipeline
    abstract val responsePipeline: HttpResponsePipeline

    override fun close() {}

    companion object {
        operator fun invoke(backendFactory: HttpClientBackendFactory) = HttpClientFactory.create(backendFactory)
    }
}

object EmptyScope : HttpClient() {
    override val attributes: Attributes = Attributes()
    override val requestPipeline: HttpRequestPipeline = HttpRequestPipeline()
    override val responsePipeline: HttpResponsePipeline = HttpResponsePipeline()
}

open class HttpCallScope(val parent: HttpClient) : HttpClient() {

    override fun close() {
        parent.close()
    }

    override val attributes = Attributes()
    override val requestPipeline: HttpRequestPipeline = HttpRequestPipeline()

    override val responsePipeline: HttpResponsePipeline = HttpResponsePipeline()
}
