/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.request

import io.ktor.application.*
import io.ktor.http.*
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
     * Parameters provided in an URL
     */
    public val queryParameters: Parameters

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
