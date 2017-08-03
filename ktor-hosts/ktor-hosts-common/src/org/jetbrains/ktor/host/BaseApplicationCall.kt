package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.nio.*

/**
 * Base class for implementing an [ApplicationCall]
 */
abstract class BaseApplicationCall(final override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()
    protected open val bufferPool: ByteBufferPool get() = NoPool
    override val parameters: ValuesMap get() = request.queryParameters
}