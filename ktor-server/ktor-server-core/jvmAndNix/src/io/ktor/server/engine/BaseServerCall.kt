/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*

@Deprecated(message = "Renamed to BaseServerCall", replaceWith = ReplaceWith("BaseServerCall"))
public typealias BaseApplicationCall = BaseServerCall

/**
 * Base class for implementing an [PipelineCall].
 */
public abstract class BaseServerCall(final override val server: Server) : PipelineCall {
    public final override val attributes: Attributes = Attributes()
    override val parameters: Parameters get() = request.queryParameters

    public abstract override val request: BaseServerRequest
    public abstract override val response: BaseServerResponse

    /**
     * Put engine response attribute. This is required for base implementation to work properly
     */
    protected fun putResponseAttribute(response: BaseServerResponse = this.response) {
        attributes.put(BaseServerResponse.EngineResponseAttributeKey, response)
    }
}
