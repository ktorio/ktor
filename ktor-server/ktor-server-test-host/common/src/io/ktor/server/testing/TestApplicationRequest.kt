/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * A test application request
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationRequest)
 *
 * @property method HTTP method to be sent or executed
 * @property uri HTTP url to sent request to or was sent to
 * @property version HTTP version to sent or executed
 * @property port (Optional) HTTP port to send request to
 * @property protocol HTTP protocol to be used or was used
 */
public class TestApplicationRequest(
    call: TestApplicationCall,
    closeRequest: Boolean,
    public var method: HttpMethod = HttpMethod.Get,
    public var uri: String = "/",
    public var port: Int? = null,
    public var version: String = "HTTP/1.1"
) : BaseApplicationRequest(call), CoroutineScope by call {

    public var protocol: String = "http"

    override val local: RequestConnectionPoint = object : RequestConnectionPoint {
        override val uri: String
            get() = this@TestApplicationRequest.uri

        override val method: HttpMethod
            get() = this@TestApplicationRequest.method

        override val scheme: String
            get() = protocol

        @Deprecated(
            "Use localPort or serverPort instead",
            level = DeprecationLevel.ERROR
        )
        override val port: Int
            get() = this@TestApplicationRequest.port
                ?: header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt()
                ?: 80

        @Deprecated(
            "Use localHost or serverHost instead",
            level = DeprecationLevel.ERROR
        )
        override val host: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: "localhost"

        override val localPort: Int
            get() = this@TestApplicationRequest.port ?: 80
        override val serverPort: Int
            get() = header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt() ?: localPort

        override val localHost: String
            get() = "localhost"
        override val serverHost: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: localHost
        override val localAddress: String
            get() = "localhost"

        override val remoteHost: String
            get() = "localhost"
        override val remotePort: Int
            get() = 0
        override val remoteAddress: String
            get() = "localhost"

        override val version: String
            get() = this@TestApplicationRequest.version

        override fun toString(): String =
            "TestConnectionPoint(uri=$uri, method=$method, version=$version, localAddress=$localAddress, " +
                "localPort=$localPort, remoteAddress=$remoteAddress, remotePort=$remotePort)"
    }

    /**
     * Request body channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationRequest.bodyChannel)
     */
    public var bodyChannel: ByteReadChannel = if (closeRequest) ByteReadChannel.Empty else ByteChannel()

    @OptIn(InternalKtorApi::class)
    override val queryParameters: Parameters by lazy {
        encodeParameters(rawQueryParameters)
            .withEmptyStringForValuelessKeys()
    }

    override val rawQueryParameters: Parameters by lazy {
        parseQueryString(queryString(), decode = false)
    }

    override val cookies: RequestCookies = RequestCookies(this)

    private var headersMap: MutableMap<String, MutableList<String>>? = mutableMapOf()

    /**
     * Adds an HTTP request header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationRequest.addHeader)
     */
    public fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name) { mutableListOf() }.add(value)
    }

    override val engineHeaders: Headers by lazy {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        Headers.build {
            map.forEach { (name, values) ->
                appendAll(name, values)
            }
        }
    }

    override val engineReceiveChannel: ByteReadChannel get() = bodyChannel
}
