package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Base class for implementing an [ApplicationCall].
 */
@EngineAPI
abstract class BaseApplicationCall(final override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()
    override val parameters: Parameters get() = request.queryParameters

    abstract override val request: BaseApplicationRequest
    abstract override val response: BaseApplicationResponse

    /**
     * Put engine response attribute. This is required for base implementation to work properly
     */
    @EngineAPI
    protected fun putResponseAttribute(response: BaseApplicationResponse = this.response) {
        attributes.put(BaseApplicationResponse.EngineResponseAtributeKey, response)
    }
}
