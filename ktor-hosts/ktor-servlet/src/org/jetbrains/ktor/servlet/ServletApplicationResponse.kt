package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.response.*
import javax.servlet.http.*

class ServletApplicationResponse(call: ServletApplicationCall,
                                 responsePipeline: RespondPipeline,
                                 val servletResponse: HttpServletResponse,
                                 val pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit,
                                 val responseChannel: () -> WriteChannel
                                 ) : BaseApplicationResponse(call, responsePipeline) {
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

    override fun channel(): WriteChannel = responseChannel()

    override fun push(block: ResponsePushBuilder.() -> Unit) {
        pushImpl(call, block, { super.push(block) })
    }
}
