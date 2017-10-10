package io.ktor.host

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import java.nio.*

/**
 * Base class for implementing an [ApplicationCall]
 */
abstract class BaseApplicationCall(final override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()
    protected open val bufferPool: ByteBufferPool get() = NoPool
    override val parameters: ValuesMap get() = request.queryParameters
}