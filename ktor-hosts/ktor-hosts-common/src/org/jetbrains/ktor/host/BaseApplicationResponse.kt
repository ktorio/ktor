package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*

abstract class BaseApplicationResponse(val call: ApplicationCall, private val responsePipeline: ApplicationResponsePipeline) : ApplicationResponse {
    private var _status: HttpStatusCode? = null
    override val pipeline: ApplicationResponsePipeline get() = responsePipeline

    override val cookies by lazy { ResponseCookies(this, call.request.origin.scheme == "https") }

    override fun status() = _status
    override fun status(value: HttpStatusCode) {
        _status = value
        setStatus(value)
    }

    protected abstract fun setStatus(statusCode: HttpStatusCode)

    override fun push(block: ResponsePushBuilder.() -> Unit) {
        if (isPrefetchLinkEnabled()) {
            val builder = DefaultResponsePushBuilder(call)

            block(builder)

            link(builder.url.build(), LinkHeader.Rel.Prefetch)
        } else {
            super.push(block)
        }
    }

    open fun isPrefetchLinkEnabled() = true

}