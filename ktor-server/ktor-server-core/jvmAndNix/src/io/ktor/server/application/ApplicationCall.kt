/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.*

private val RECEIVE_TYPE_KEY: AttributeKey<TypeInfo> = AttributeKey("ReceiveType")

/**
 * A single act of communication between a client and server.
 * @see [io.ktor.server.request.ApplicationRequest]
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public interface ApplicationCall {
    /**
     * An application being called.
     */
    public val application: Application

    /**
     * An [ApplicationRequest] that is a client request.
     */
    public val request: ApplicationRequest

    /**
     * An [ApplicationResponse] that is a server response.
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
}

/**
 * Indicates if a response is sent.
 */
public val ApplicationCall.isHandled: Boolean get() = response.isCommitted

/**
 * The [TypeInfo] recorded from the last [call.receive<Type>()] call.
 */
public var ApplicationCall.receiveType: TypeInfo
    get() = attributes[RECEIVE_TYPE_KEY]
    internal set(value) {
        attributes.put(RECEIVE_TYPE_KEY, value)
    }
