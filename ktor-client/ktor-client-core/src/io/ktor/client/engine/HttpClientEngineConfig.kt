package io.ktor.client.engine

import kotlinx.coroutines.experimental.*

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
}
