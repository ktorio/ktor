package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import java.io.*


interface HttpClientEngine : Closeable {
    suspend fun makeRequest(request: HttpRequest): HttpResponseBuilder
}

interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    fun create(block: T.() -> Unit = {}): HttpClientEngine
}

fun <T : HttpClientEngineConfig> HttpClientEngineFactory<T>.config(nested: T.() -> Unit): HttpClientEngineFactory<T> =
        object : HttpClientEngineFactory<T> {
            override fun create(block: T.() -> Unit): HttpClientEngine = this@config.create {
                block()
                nested()
            }
        }
