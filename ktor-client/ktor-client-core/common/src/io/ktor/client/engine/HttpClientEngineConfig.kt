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
        "Custom dispatcher is deprecated. Consider using threadsCount instead.",
        level = DeprecationLevel.ERROR
    )
    var dispatcher: CoroutineDispatcher? = null

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
