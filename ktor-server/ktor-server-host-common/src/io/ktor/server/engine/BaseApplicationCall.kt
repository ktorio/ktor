package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Base class for implementing an [ApplicationCall].
 */
abstract class BaseApplicationCall(final override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()
    override val parameters: Parameters get() = request.queryParameters
}
