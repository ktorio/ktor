package io.ktor.client.engine

import io.ktor.client.response.*
import kotlinx.coroutines.*

/**
 * Base configuration for [HttpClientEngine].
 */
open class HttpClientEngineConfig {
    /**
     * The [CoroutineDispatcher] that will be used for the client requests.
     */
    var dispatcher: CoroutineDispatcher? = null

    /**
     * Enable http pipelining
     */
    var pipelining: Boolean = true

    /**
     * Configuration for http response.
     */
    val response: HttpResponseConfig = HttpResponseConfig()
}
