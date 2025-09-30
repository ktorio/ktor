/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.http.*
import io.ktor.util.*
import java.net.*

@InternalAPI
internal interface URLConnectionFactory {
    /**
 * Open an HttpURLConnection for the specified URL string, using the factory's configured proxy if present.
 *
 * @param urlString The URL to connect to, expressed as a string.
 * @return An HttpURLConnection for the specified URL.
 */
operator fun invoke(urlString: String): HttpURLConnection
    /**
     * Determine the HTTP protocol version associated with the given URL connection.
     *
     * @param connection The HttpURLConnection to inspect.
     * @return The HTTP protocol version for the connection. The default implementation returns `HttpProtocolVersion.HTTP_1_1`.
     */
    fun protocolFromRequest(connection: HttpURLConnection): HttpProtocolVersion {
        // This is not exposed with HttpEngine
        return HttpProtocolVersion.HTTP_1_1
    }

    @InternalAPI
    class StandardURLConnectionFactory(val config: AndroidEngineConfig) : URLConnectionFactory {
        /**
         * Create an HttpURLConnection for the given URL string using the engine's proxy when configured.
         *
         * @param urlString The URL as a string to open a connection to.
         * @return An HttpURLConnection for the specified URL; if the engine config defines a proxy, the connection is opened via that proxy. 
         */
        override operator fun invoke(urlString: String): HttpURLConnection {
            val url = URL(urlString)
            val connection: URLConnection = config.proxy?.let { url.openConnection(it) } ?: url.openConnection()
            return connection as HttpURLConnection
        }
    }
}
