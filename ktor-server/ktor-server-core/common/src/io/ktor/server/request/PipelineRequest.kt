/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.utils.io.*

/**
 * A client's request.
 * To learn how to handle incoming requests, see [Handling requests](https://ktor.io/docs/requests.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest)
 *
 * @see [io.ktor.server.application.ApplicationCall]
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public interface ApplicationRequest {

    /**
     * Provides access to headers for the current request.
     * You can also get access to specific headers using dedicated extension functions,
     * such as [acceptEncoding], [contentType], [cacheControl], and so on.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.headers)
     */
    public val headers: Headers

    /**
     * An [ApplicationCall] instance this [ApplicationRequest] is attached to.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.call)
     */
    public val call: ApplicationCall

    /**
     * Provides access to connection details such as a host name, port, scheme, etc.
     * To get information about a request passed through an HTTP proxy or a load balancer,
     * install the ForwardedHeaders/XForwardedHeader plugin and use the [origin] property.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.local)
     */
    public val local: RequestConnectionPoint

    /**
     * Provides access to decoded parameters of a URL query string.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.queryParameters)
     */
    public val queryParameters: Parameters

    /**
     * Provides access to parameters of a URL query string.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.rawQueryParameters)
     */
    public val rawQueryParameters: Parameters

    /**
     * Provides access to cookies for this request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.cookies)
     */
    public val cookies: RequestCookies

    /**
     * Receives a raw body payload as a channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ApplicationRequest.receiveChannel)
     */
    public fun receiveChannel(): ByteReadChannel
}

/**
 * A client's request that is used in [ApplicationPlugin].
 * To learn how to handle incoming requests, see [Handling requests](https://ktor.io/docs/requests.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.PipelineRequest)
 *
 * @see [PipelineCall]
 * @see [io.ktor.server.response.PipelineResponse]
 */
public interface PipelineRequest : ApplicationRequest {
    /**
     * An [PipelineCall] instance this [PipelineRequest] is attached to.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.PipelineRequest.call)
     */
    public override val call: PipelineCall

    /**
     * A pipeline for receiving content.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.PipelineRequest.pipeline)
     */
    public val pipeline: ApplicationReceivePipeline

    /**
     * Overrides request headers. Will remove header [name] if passed [values] is `null` or set [values] otherwise.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.PipelineRequest.setHeader)
     */
    @InternalAPI
    public fun setHeader(name: String, values: List<String>?)

    /**
     * Overrides request body. It's a caller responsibility to close the original channel if it's not needed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.PipelineRequest.setReceiveChannel)
     */
    @InternalAPI
    public fun setReceiveChannel(channel: ByteReadChannel)
}

/**
 * Internal helper function to encode raw parameters. Should not be used directly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.encodeParameters)
 */
public fun ApplicationRequest.encodeParameters(parameters: Parameters): Parameters {
    return ParametersBuilder().apply {
        rawQueryParameters.names().forEach { key ->
            val values = parameters.getAll(key)?.map { it.decodeURLQueryComponent(plusIsSpace = true) }.orEmpty()
            appendAll(key.decodeURLQueryComponent(), values)
        }
    }.build()
}
