/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.http.*
import java.net.*

internal interface URLConnectionFactory {
    operator fun invoke(urlString: String): HttpURLConnection
    fun protocolFromRequest(connection: HttpURLConnection): HttpProtocolVersion {
        // This is not exposed with HttpEngine
        return HttpProtocolVersion.HTTP_1_1
    }

    class StandardURLConnectionFactory(val config: AndroidEngineConfig) : URLConnectionFactory {
        override operator fun invoke(urlString: String): HttpURLConnection {
            val url = URL(urlString)
            val connection: URLConnection = config.proxy?.let { url.openConnection(it) } ?: url.openConnection()
            return connection as HttpURLConnection
        }
    }
}
