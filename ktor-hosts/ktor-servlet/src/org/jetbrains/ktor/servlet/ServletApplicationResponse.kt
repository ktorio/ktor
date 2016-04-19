package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import java.io.*
import javax.servlet.http.*

class ServletApplicationResponse(val call: ServletApplicationCall, val servletResponse: HttpServletResponse) : BaseApplicationResponse() {
    var _status: HttpStatusCode? = null

    override fun status(value: HttpStatusCode) {
        _status = value
        servletResponse.status = value.value
    }
    override fun status(): HttpStatusCode? = _status

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            servletResponse.addHeader(name, value)
        }

        override fun getHostHeaderNames(): List<String> = servletResponse.headerNames.toList()
        override fun getHostHeaderValues(name: String): List<String> = servletResponse.getHeaders(name).toList()
    }


    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        servletResponse.outputStream.body()
    }

    override val channel = Interceptable0<AsyncWriteChannel> {
        if (!call.asyncStarted) {
            call.startAsync()
        }
        ServletAsyncWriteChannel(servletResponse.outputStream)
    }
}