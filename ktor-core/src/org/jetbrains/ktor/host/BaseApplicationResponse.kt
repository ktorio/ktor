package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

abstract class BaseApplicationResponse() : ApplicationResponse {
    private var _status: HttpStatusCode? = null

    override val cookies = ResponseCookies(this)

    override fun status() = _status
    override fun status(value: HttpStatusCode) {
        _status = value
        setStatus(value)
    }

    protected abstract fun setStatus(statusCode: HttpStatusCode)
}