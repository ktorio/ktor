package io.ktor.client

import io.ktor.client.backend.*
import io.ktor.client.call.*
import io.ktor.client.pipeline.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.util.*


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
