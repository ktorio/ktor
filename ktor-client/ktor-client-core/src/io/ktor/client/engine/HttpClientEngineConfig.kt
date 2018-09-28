package io.ktor.client.engine

import kotlinx.coroutines.*

/**
 * Base configuration for [HttpClientEngine].
 */
open class HttpClientEngineConfig {
    /**
     * The [CoroutineDispatcher] that will be used for the client requests.
     */
    @Deprecated(
        "Custom dispatcher is deprecated. Consider using threadsCount instead.",
        level = DeprecationLevel.ERROR
    )
    var dispatcher: CoroutineDispatcher? = null

    /**
     * Network threads count
     */
    var threadsCount: Int = 4

    /**
     * Enable http pipelining
     */
    var pipelining: Boolean = true
}
