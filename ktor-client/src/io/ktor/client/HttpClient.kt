package io.ktor.client

import io.ktor.client.backend.HttpClientBackend
import io.ktor.client.backend.HttpClientBackendFactory
import io.ktor.client.call.HttpClientCall
import io.ktor.client.pipeline.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.safeAs


class HttpClient private constructor(val backend: HttpClientBackend) {
    companion object {
        operator fun invoke(backendFactory: HttpClientBackendFactory): HttpClientScope {
            val backend = backendFactory()

            return HttpCallScope(EmptyScope).config {
                install("backend") {
                    requestPipeline.intercept(HttpRequestPipeline.Send) { builder ->
                        val request = builder.safeAs<HttpRequestBuilder>()?.build() ?: return@intercept
                        val response = backend.makeRequest(request)
                        proceedWith(HttpClientCall(request, response.build(), context))
                    }
                }
            }.default()
        }
    }
}
