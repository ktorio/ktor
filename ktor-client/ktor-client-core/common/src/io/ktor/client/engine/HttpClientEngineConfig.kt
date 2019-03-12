package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.response.*
import io.ktor.util.*
import kotlinx.coroutines.*

/**
 * Base configuration for [HttpClientEngine].
 */
@HttpClientDsl
open class HttpClientEngineConfig {
    /**
     * The [CoroutineDispatcher] that will be used for the client requests.
     */
    @Deprecated(
        "Binary compatibility.",
        level = DeprecationLevel.HIDDEN
    )
    var dispatcher: CoroutineDispatcher?
        get() = null
        set(_) {
            throw UnsupportedOperationException("Custom dispatcher is deprecated. Use threadsCount instead.")
        }

    /**
     * Network threads count
     */
    @KtorExperimentalAPI
    var threadsCount: Int = 4

    /**
     * Enable http pipelining
     */
    var pipelining: Boolean = false

    /**
     * Configuration for http response.
     */
    val response: HttpResponseConfig = HttpResponseConfig()
}
