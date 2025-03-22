/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.server.application.*

/**
 * A server's response.
 * To learn how to send responses inside route handlers, see [Sending responses](https://ktor.io/docs/responses.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse)
 *
 * @see [ApplicationCall]
 * @see [io.ktor.server.request.ApplicationRequest]
 */
public interface ApplicationResponse {
    /**
     * Provides access to headers for the current response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.headers)
     */
    public val headers: ResponseHeaders

    /**
     * An [ApplicationCall] instance this [ApplicationResponse] is attached to.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.call)
     */
    public val call: ApplicationCall

    /**
     * Indicates that this response is already committed and no further changes are allowed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.isCommitted)
     */
    public val isCommitted: Boolean

    /**
     * Indicates that this response is already fully sent to the client.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.isSent)
     */
    public val isSent: Boolean

    /**
     * Provides access to cookies for this response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.cookies)
     */
    public val cookies: ResponseCookies

    /**
     * Returns a response status code or `null` if a status code is not set.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.status)
     */
    public fun status(): HttpStatusCode?

    /**
     * Specifies a status code for a response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.status)
     */
    public fun status(value: HttpStatusCode)

    /**
     * Produces HTTP/2 push from a server to a client or sets an HTTP/1.x hint header
     * or does nothing. Exact behaviour is up to engine implementation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.ApplicationResponse.push)
     */
    @UseHttp2Push
    public fun push(builder: ResponsePushBuilder)
}

/**
 * A server's response that is used in [ApplicationPlugin].
 * To learn how to send responses inside route handlers, see [Sending responses](https://ktor.io/docs/responses.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.PipelineResponse)
 *
 * @see [PipelineCall]
 * @see [io.ktor.server.request.PipelineRequest]
 */
public interface PipelineResponse : ApplicationResponse {
    /**
     * An [PipelineCall] instance this [PipelineResponse] is attached to.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.PipelineResponse.call)
     */
    public override val call: PipelineCall

    /**
     * A pipeline for sending content.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.response.PipelineResponse.pipeline)
     */
    public val pipeline: ApplicationSendPipeline
}
