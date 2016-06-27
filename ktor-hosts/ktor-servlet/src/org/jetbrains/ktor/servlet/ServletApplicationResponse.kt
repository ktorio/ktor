package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import javax.servlet.http.*

class ServletApplicationResponse(val call: ServletApplicationCall, val servletResponse: HttpServletResponse) : BaseApplicationResponse() {
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

    override fun channel(): AsyncWriteChannel {
        if (BaseApplicationCall.ResponseChannelOverride in call.attributes) {
            return call.attributes[BaseApplicationCall.ResponseChannelOverride]
        }

        call.ensureAsync()
        return ServletAsyncWriteChannel(servletResponse.outputStream)
    }
}