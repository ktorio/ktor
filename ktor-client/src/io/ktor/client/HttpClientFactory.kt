package io.ktor.client

import io.ktor.client.backend.HttpClientBackendFactory
import io.ktor.client.call.HttpClientCall
import io.ktor.client.pipeline.config
import io.ktor.client.pipeline.default
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.response.HttpResponsePipeline
import io.ktor.util.safeAs


object HttpClientFactory {
    fun create(backendFactory: HttpClientBackendFactory): HttpClient {
        val backend = backendFactory()

        return HttpCallScope(EmptyScope).config {
            install("backend") {
                requestPipeline.intercept(HttpRequestPipeline.Send) { builder ->
                    val request = builder.safeAs<HttpRequestBuilder>()?.build() ?: return@intercept
                    val response = backend.makeRequest(request)
                    proceedWith(HttpClientCall(request, response.build(), context))
                }

                responsePipeline.intercept(HttpResponsePipeline.After) { container ->
                    container.response.close()
                }
            }
        }.default()
    }
}
