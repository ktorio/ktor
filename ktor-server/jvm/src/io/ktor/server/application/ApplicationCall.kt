/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.*

/**
 * Represents a single act of communication between a client and server.
 */
public interface ApplicationCall {
    /**
     * Application being called
     */
    public val application: Application

    /**
     * [ApplicationRequest] that represents a client request.
     */
    public val request: ApplicationRequest

    /**
     * [ApplicationRequest] that represents a server response.
     */
    public val response: ApplicationResponse

    /**
     * [Attributes] attached to this call.
     */
    public val attributes: Attributes

    /**
     * Parameters associated with this call.
     */
    public val parameters: Parameters

    /**
     * Allows you to execute a [handler] after the current call is finished.
     *
     * @param throwable An exception that can take place before the call has finished.
     * */
    public fun afterFinish(handler: (Throwable?) -> Unit)
}

public interface ApplicationCallWithContext : ApplicationCall, CoroutineScope {
    override fun afterFinish(handler: (Throwable?) -> Unit) {
        coroutineContext.job.invokeOnCompletion(handler)
    }
}
