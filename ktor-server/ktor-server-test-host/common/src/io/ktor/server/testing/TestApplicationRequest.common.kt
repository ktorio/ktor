/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

/**
 * Represents a test application request
 *
 * @property method HTTP method to be sent or executed
 * @property uri HTTP url to sent request to or was sent to
 * @property version HTTP version to sent or executed
 * @property protocol HTTP protocol to be used or was used
 */
public class TestApplicationRequest constructor(
    call: TestApplicationCall,
    closeRequest: Boolean,
    method: HttpMethod = HttpMethod.Get,
    uri: String = "/",
    version: String = "HTTP/1.1"
) : BaseApplicationRequest(call), CoroutineScope by call {
    var method: HttpMethod by shared(method)
    var uri: String  by shared(uri)
    var version: String  by shared(version)
    var protocol: String  by shared("http")

    override val local: RequestConnectionPoint = object : RequestConnectionPoint {
        override val uri: String
            get() = this@TestApplicationRequest.uri

        override val method: HttpMethod
            get() = this@TestApplicationRequest.method

        override val scheme: String
            get() = protocol

        override val port: Int
            get() = header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt() ?: 80

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
    var bodyChannel: ByteReadChannel by atomic(if (closeRequest) ByteReadChannel.Empty else ByteChannel())

    override val queryParameters: Parameters by lazy { parseQueryString(queryString()) }

    override val cookies: RequestCookies = RequestCookies(this)

    private var headersMap: MutableMap<String, MutableList<String>>? by atomic(ConcurrentMap())

    /**
     * Add HTTP request header
     */
    public fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name) { ConcurrentList() }.add(value)
    }

    override val headers: Headers by lazy {
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
