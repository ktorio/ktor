package io.ktor.client.engine

import io.ktor.client.call.*
import io.ktor.client.request.*
import java.io.*


interface HttpClientEngine : Closeable {
    fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest
}

interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    fun create(block: T.() -> Unit = {}): HttpClientEngine
}

fun <T : HttpClientEngineConfig> HttpClientEngineFactory<T>.config(nested: T.() -> Unit): HttpClientEngineFactory<T> {
    val parent = this

    return object : HttpClientEngineFactory<T> {
        override fun create(block: T.() -> Unit): HttpClientEngine = parent.create {
            nested()
            block()
        }
    }
}
