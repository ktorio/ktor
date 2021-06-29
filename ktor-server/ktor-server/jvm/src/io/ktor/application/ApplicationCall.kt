/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.application

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Represents a single act of communication between client and server.
 */
public interface ApplicationCall {
    /**
     * Application being called
     */
    public val application: Application

    /**
     * Client request
     */
    public val request: ApplicationRequest

    /**
     * Server response
     */
    public val response: ApplicationResponse

    /**
     * Attributes attached to this instance
     */
    public val attributes: Attributes

    /**
     * Parameters associated with this call
     */
    public val parameters: Parameters
}
