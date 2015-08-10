package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.io.*
import java.util.*
import javax.servlet.*
import javax.servlet.http.*

public class ServletApplicationRequest(override val application: Application,
                                       private val servletRequest: HttpServletRequest,
                                       private val servletResponse: HttpServletResponse) : ApplicationRequest {
    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(servletRequest.method, servletRequest.requestURI, servletRequest.protocol)
    }

    override val parameters: Map<String, List<String>> by lazy {
        val result = HashMap<String, MutableList<String>>()
        val parametersMap = servletRequest.parameterMap
        if (parametersMap != null) {
            for ((key, values) in parametersMap) {
                if (values != null) {
                    result.getOrPut(key, { arrayListOf() }).addAll(values)
                }
            }
        }
        result
    }

    override val headers: Map<String, String> by lazy {
        // TODO: consider doing the opposite, splitting headers by comma and making it String to List<String> map
        servletRequest.headerNames.asSequence().toMap({ it }, {
            servletRequest.getHeaders(it).asSequence().join(", ")
        })
    }

    private var asyncContext: AsyncContext? = null
    fun continueAsync(asyncContext: AsyncContext) {
        this.asyncContext = asyncContext
    }

    var response: Response? = null
    override fun respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val currentResponse = response
        if (currentResponse != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = Response()
        return response!!.handle()
    }

    inner class Response : ApplicationResponse {
        override fun header(name: String, value: String): ApplicationResponse {
            servletResponse.setHeader(name, value)
            return this
        }

        override fun status(code: Int): ApplicationResponse {
            servletResponse.status = code
            return this
        }

        override fun content(text: String, encoding: String): ApplicationResponse {
            servletResponse.characterEncoding = encoding
            val writer = servletResponse.writer
            writer?.write(text)
            return this
        }

        override fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse {
            val writer = servletResponse.writer
            writer.streamer()
            return this
        }

        override fun content(bytes: ByteArray): ApplicationResponse {
            val writer = servletResponse.outputStream
            writer?.write(bytes)
            return this
        }

        override fun send(): ApplicationRequestStatus {
            servletResponse.flushBuffer()
            if (asyncContext != null) {
                asyncContext?.complete()
            }
            return ApplicationRequestStatus.Handled
        }

        override fun sendRedirect(url: String): ApplicationRequestStatus {
            servletResponse.sendRedirect(url)
            servletResponse.flushBuffer()
            return ApplicationRequestStatus.Handled
        }

    }

}