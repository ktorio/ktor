package io.ktor.client.backend

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import java.io.*


interface HttpClientBackend : Closeable {
    suspend fun makeRequest(request: HttpRequest): HttpResponseBuilder
}

interface HttpClientBackendFactory<out T : HttpClientBackendConfig> {
    fun create(block: T.() -> Unit = {}): HttpClientBackend
}

fun <T : HttpClientBackendConfig> HttpClientBackendFactory<T>.config(nested: T.() -> Unit): HttpClientBackendFactory<T> =
        object : HttpClientBackendFactory<T> {
            override fun create(block: T.() -> Unit): HttpClientBackend = this@config.create {
                block()
                nested()
            }
        }
