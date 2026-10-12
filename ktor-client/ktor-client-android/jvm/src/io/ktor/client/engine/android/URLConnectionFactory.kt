/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import java.io.Closeable
import java.net.*

internal interface URLConnectionFactory : Closeable {
    operator fun invoke(urlString: String): HttpURLConnection

    override fun close() {}

    class StandardURLConnectionFactory(val config: AndroidEngineConfig) : URLConnectionFactory {
        override operator fun invoke(urlString: String): HttpURLConnection {
            val url = URL(urlString)
            val connection: URLConnection = config.proxy?.let { url.openConnection(it) } ?: url.openConnection()
            return connection as HttpURLConnection
        }
    }
}
