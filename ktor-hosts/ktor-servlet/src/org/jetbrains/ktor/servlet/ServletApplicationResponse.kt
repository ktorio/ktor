package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import javax.servlet.*
import javax.servlet.http.*

class ServletApplicationResponse(val call: ServletApplicationCall, val servletRequest: HttpServletRequest, val servletResponse: HttpServletResponse) : BaseApplicationResponse() {
    var _status: HttpStatusCode? = null
    override val status = Interceptable1<HttpStatusCode, Unit> { code ->
        _status = code
        servletResponse.status = code.value
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            servletResponse.addHeader(name, value)
        }

        override fun getHostHeaderNames(): List<String> = servletResponse.headerNames.toList()
        override fun getHostHeaderValues(name: String): List<String> = servletResponse.getHeaders(name).toList()
    }

    override fun status(): HttpStatusCode? = _status

    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        servletResponse.outputStream.body()
        ApplicationCallResult.Handled
    }

    init {
        send.intercept { obj, next ->
            if (obj is StreamContentProvider) {
                send(obj.stream())
            } else if (obj is LocalFileContent) {
                servletResponse.addHeader(HttpHeaders.ContentType, obj.contentType.toString())
                send(obj.file)
            } else {
                next(obj)
            }
        }
    }

    fun send(inputStream: InputStream): ApplicationCallResult {
        var async = false
        stream.execute {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()

                val pump = AsyncInputStreamPump(inputStream, asyncContext, this)
                pump.start()
                async = true
            } else {
                inputStream.use { it.copyTo(this) }
            }
        }
        return if (async) {
            ApplicationCallResult.Asynchronous
        } else {
            ApplicationCallResult.Handled
        }
    }

    fun send(file: File): ApplicationCallResult {
        var async = false

        stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()

                val pump = AsyncFilePump(file.toPath(), asyncContext, servletResponse.outputStream)
                pump.start()
                async = true
            } else {
                file.inputStream().use { it.copyTo(this) }
            }
        }

        return if (async) {
            ApplicationCallResult.Asynchronous
        } else {
            ApplicationCallResult.Handled
        }
    }

    private fun startAsync(): AsyncContext {
        val asyncContext = servletRequest.startAsync(servletRequest, servletResponse)
        // asyncContext.timeout = ?
        call.continueAsync(asyncContext)

        return asyncContext
    }
}