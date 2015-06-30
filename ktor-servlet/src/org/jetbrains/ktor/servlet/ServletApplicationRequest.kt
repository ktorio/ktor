package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.io.*
import java.util.*
import javax.servlet.http.*

public class ServletApplicationRequest(override val application: Application,
                                       private val servletRequest: HttpServletRequest,
                                       private val servletResponse: HttpServletResponse) : ApplicationRequest {
    override val uri: String = servletRequest.getRequestURI()
    override val httpMethod: String = servletRequest.getMethod()

    override val parameters: Map<String, List<String>>

    init {
        val result = HashMap<String, MutableList<String>>()
        result.put("@method", arrayListOf(httpMethod))
        val parametersMap = servletRequest.getParameterMap()
        if (parametersMap != null) {
            for ((key, values) in parametersMap) {
                if (values != null) {
                    val list = result.getOrPut(key, { arrayListOf() })
                    for (value in values) {
                        list.add(value)
                    }
                }
            }
        }

        for ((key, value) in headers()) {
            result.getOrPut(key, { arrayListOf() }).addAll(Headers.splitKnownHeaders(key, value))
        }

        parameters = result
    }

    override fun header(name: String): String? = servletRequest.getHeader(name)

    override fun headers(): Map<String, String> {
        return servletRequest.getHeaderNames().asSequence().toMap({ it }, { servletRequest.getHeader(it) })
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

        override fun header(name: String, value: Int): ApplicationResponse {
            servletResponse.setIntHeader(name, value)
            return this
        }

        override fun status(code: Int): ApplicationResponse {
            servletResponse.setStatus(code)
            return this
        }

        override fun contentType(value: String): ApplicationResponse {
            servletResponse.setContentType(value)
            return this
        }

        override fun content(text: String, encoding: String): ApplicationResponse {
            servletResponse.setCharacterEncoding(encoding)
            val writer = servletResponse.getWriter()
            writer?.write(text)
            return this
        }

        override fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse {
            val writer = servletResponse.getWriter()
            writer.streamer()
            return this
        }

        override fun content(bytes: ByteArray): ApplicationResponse {
            val writer = servletResponse.getOutputStream()
            writer?.write(bytes)
            return this
        }

        override fun send(): ApplicationRequestStatus {
            servletResponse.flushBuffer()
            return ApplicationRequestStatus.Handled
        }

        override fun sendRedirect(url: String): ApplicationRequestStatus {
            servletResponse.sendRedirect(url)
            servletResponse.flushBuffer()
            return ApplicationRequestStatus.Handled
        }

    }
}