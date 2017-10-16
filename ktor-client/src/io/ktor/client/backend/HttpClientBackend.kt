package io.ktor.client.backend

import io.ktor.client.request.HttpRequest
import io.ktor.client.response.HttpResponseBuilder
import java.io.Closeable


interface HttpClientBackend : Closeable {
    suspend fun makeRequest(request: HttpRequest): HttpResponseBuilder
}

interface HttpClientBackendFactory {
    operator fun invoke(): HttpClientBackend
}
