/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.jvm.*

/**
 * Represents a test application request
 *
 * @property method HTTP method to be sent or executed
 * @property uri HTTP url to sent request to or was sent to
 * @property version HTTP version to sent or executed
 * @property port (Optional) HTTP port to send request to
 * @property protocol HTTP protocol to be used or was used
 */
@Suppress("DEPRECATION")
public class TestApplicationRequest constructor(
    call: TestApplicationCall,
    closeRequest: Boolean,
    method: HttpMethod = HttpMethod.Get,
    uri: String = "/",
    port: Int? = null,
    version: String = "HTTP/1.1"
) : BaseApplicationRequest(call), CoroutineScope by call {

    var uri by shared(uri)
    var protocol: String by shared("http")
    var port by shared(port)
    var version by shared(version)
    var method by shared(method)

    override val local: RequestConnectionPoint = object : RequestConnectionPoint {
        override val uri: String
            get() = this@TestApplicationRequest.uri

        override val method: HttpMethod
            get() = this@TestApplicationRequest.method

        override val scheme: String
            get() = protocol

        override val port: Int
            get() = this@TestApplicationRequest.port
                ?: header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt()
                ?: 80

        override val host: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: "localhost"

        override val remoteHost: String
            get() = "localhost"

        override val version: String
            get() = this@TestApplicationRequest.version
    }

    /**
     * Request body channel
     */
    var bodyChannel: ByteReadChannel by shared(if (closeRequest) ByteReadChannel.Empty else ByteChannel())

    override val queryParameters: Parameters by sharedLazy { encodeParameters(rawQueryParameters) }

    override val rawQueryParameters: Parameters by sharedLazy {
        parseQueryString(queryString(), decode = false)
    }

    override val cookies: RequestCookies = RequestCookies(this)

    private var headersMap: MutableMap<String, MutableList<String>>? by shared(sharedMap())

    /**
     * Add HTTP request header
     */
    public fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name) { sharedListOf() }.add(value)
    }

    override val headers: Headers by sharedLazy {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        Headers.build {
            map.forEach { (name, values) ->
                appendAll(name, values)
            }
        }
    }

    override fun receiveChannel(): ByteReadChannel = bodyChannel
}

/**
 * Set HTTP request body text content
 */
public fun TestApplicationRequest.setBody(value: String) {
    setBody(value.toByteArray())
}

/**
 * Set HTTP request body bytes
 */
public fun TestApplicationRequest.setBody(value: ByteArray) {
    bodyChannel = ByteReadChannel(value)
}

/**
 * Set HTTP request body from [ByteReadPacket]
 */
public fun TestApplicationRequest.setBody(value: ByteReadPacket) {
    bodyChannel = ByteReadChannel(value.readBytes())
}
