package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import javax.servlet.http.*

class ServletApplicationResponse(call: ServletApplicationCall,
                                 val servletResponse: HttpServletResponse,
                                 val pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit) : BaseApplicationResponse(call) {
    private val servletCall = call

    override fun setStatus(statusCode: HttpStatusCode) {
        servletResponse.status = statusCode.value
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            servletResponse.addHeader(name, value)
        }

        override fun getHostHeaderNames(): List<String> = servletResponse.headerNames.toList()
        override fun getHostHeaderValues(name: String): List<String> = servletResponse.getHeaders(name).toList()
    }

    override fun channel(): WriteChannel {
        if (BaseApplicationCall.ResponseChannelOverride in call.attributes) {
            return call.attributes[BaseApplicationCall.ResponseChannelOverride]
        }

        servletCall.ensureAsync()
        return ServletWriteChannel(servletResponse.outputStream)
    }

    override fun push(block: ResponsePushBuilder.() -> Unit) {
        pushImpl(call, block, { super.push(block) })
    }
}
