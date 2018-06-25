package io.ktor.client.engine

import kotlinx.coroutines.experimental.*
import javax.net.ssl.*

/**
 * Base configuration for [HttpClientEngine].
 */
open class HttpClientEngineConfig {
    /**
     * Optional Java's SSLContext allowing to set custom keys,
     * trust manager or custom source for secure random data
     */
    var sslContext: SSLContext? = null

    /**
     * Enable http pipelining
     */
    var pipelining: Boolean = true

    /**
     * The [CoroutineDispatcher] that will be used for the client requests.
     */
    var dispatcher: CoroutineDispatcher? = null
}
