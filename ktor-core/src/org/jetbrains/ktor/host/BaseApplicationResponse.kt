package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*

abstract class BaseApplicationResponse() : ApplicationResponse {
    protected abstract val channel: Interceptable0<AsyncWriteChannel>
    private var _status: HttpStatusCode? = null

    override val cookies = ResponseCookies(this)

    override fun channel() = channel.execute()
    override fun interceptChannel(handler: (() -> AsyncWriteChannel) -> AsyncWriteChannel) = channel.intercept(handler)

    override fun status() = _status
    override fun status(value: HttpStatusCode) {
        _status = value
        setStatus(value)
    }

    protected abstract fun setStatus(statusCode: HttpStatusCode)
}