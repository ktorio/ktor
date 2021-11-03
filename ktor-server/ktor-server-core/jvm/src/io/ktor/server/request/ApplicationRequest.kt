/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.utils.io.*

/**
 * Represents client's request
 */
public interface ApplicationRequest {
    /**
     * [ApplicationCall] instance this ApplicationRequest is attached to
     */
    public val call: ApplicationCall

    /**
     * Pipeline for receiving content
     */
    public val pipeline: ApplicationReceivePipeline

    /**
     * Decoded parameters provided in a URL
     */
    public val queryParameters: Parameters

    /**
     * Parameters provided in a URL
     */
    public val rawQueryParameters: Parameters

    /**
     * Headers for this request
     */
    public val headers: Headers

    /**
     * Contains http request and connection details such as a host name used to connect, port, scheme and so on.
     * No proxy headers could affect it. Use [ApplicationRequest.origin] if you need override headers support
     */
    public val local: RequestConnectionPoint

    /**
     * Cookies for this request
     */
    public val cookies: RequestCookies

    /**
     * Request's body channel (for content only)
     */
    public fun receiveChannel(): ByteReadChannel
}

/**
 * Internal helper function to encode raw parameters. Should not be used directly.
 */
public fun ApplicationRequest.encodeParameters(parameters: Parameters): Parameters {
    return ParametersBuilder().apply {
        rawQueryParameters.names().forEach { key ->
            val values = parameters.getAll(key)?.map { it.decodeURLQueryComponent(plusIsSpace = true) }.orEmpty()
            appendAll(key.decodeURLQueryComponent(), values)
        }
    }.build()
}
