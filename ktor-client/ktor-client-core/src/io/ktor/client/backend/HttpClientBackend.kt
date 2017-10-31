package io.ktor.client.backend

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import java.io.*


interface HttpClientBackend : Closeable {
    suspend fun makeRequest(request: HttpRequest): HttpResponseBuilder
}

typealias HttpClientBackendFactory = (HttpClientBackendConfig.() -> Unit) -> HttpClientBackend

fun HttpClientBackendFactory.config(nested: HttpClientBackendConfig.() -> Unit): HttpClientBackendFactory = {
    this { it(); nested() }
}
