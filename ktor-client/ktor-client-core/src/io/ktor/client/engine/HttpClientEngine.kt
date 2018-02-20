package io.ktor.client.engine

import io.ktor.client.call.*
import io.ktor.client.request.*
import java.io.*

/**
 * Base interface use to define engines for [HttpClient].
 */
interface HttpClientEngine : Closeable {
    /**
     * Creates a new [HttpRequest] specific for this engine, using a request [builder]
     * and a [call] with the request configured.
     */
    fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest
}

/**
 * Factory of [HttpClientEngine] with a specific [T] of [HttpClientEngineConfig].
 */
interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    /**
     * Creates a new [HttpClientEngine] optionally specifying a [block] configuring [T].
     */
    fun create(block: T.() -> Unit = {}): HttpClientEngine
}

/**
 * Creates a new [HttpClientEngineFactory] based on this one
 * with further configurations from the [nested] block.
 */
fun <T : HttpClientEngineConfig> HttpClientEngineFactory<T>.config(nested: T.() -> Unit): HttpClientEngineFactory<T> {
    val parent = this

    return object : HttpClientEngineFactory<T> {
        override fun create(block: T.() -> Unit): HttpClientEngine = parent.create {
            nested()
            block()
        }
    }
}
