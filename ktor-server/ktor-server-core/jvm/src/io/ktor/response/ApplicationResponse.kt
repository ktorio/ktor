/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.response

import io.ktor.application.*
import io.ktor.http.*

/**
 * Represents server's response
 */
public interface ApplicationResponse {
    /**
     * [ApplicationCall] instance this ApplicationResponse is attached to
     */
    public val call: ApplicationCall

    /**
     * Pipeline for sending content
     */
    public val pipeline: ApplicationSendPipeline

    /**
     * Headers for this response
     */
    public val headers: ResponseHeaders

    /**
     * Cookies for this response
     */
    public val cookies: ResponseCookies

    /**
     * Currently set status code for this response, or null if none was set
     */
    public fun status(): HttpStatusCode?

    /**
     * Set status for this response
     */
    public fun status(value: HttpStatusCode)

    /**
     * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
     * or does nothing. Exact behaviour is up to engine implementation.
     */
    @UseHttp2Push
    public fun push(builder: ResponsePushBuilder)
}
