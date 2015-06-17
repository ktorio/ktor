package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.io.*
import java.util.*
import javax.servlet.http.*

public class ServletApplicationRequest(override val application: Application,
                                       private val request: HttpServletRequest,
                                       private val response: HttpServletResponse) : ApplicationRequest {
    override val uri: String = request.getRequestURI()
    override val httpMethod: String = request.getMethod()

    var appResponse: Response? = null

    override val parameters: Map<String, List<String>>

    init {
        val result = HashMap<String, MutableList<String>>()
        result.put("@method", arrayListOf(httpMethod))
        val parametersMap = request.getParameterMap()
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

    override fun header(name: String): String? = request.getHeader(name)

    override fun headers(): Map<String, String> {
        return request.getHeaderNames().asSequence().toMap({ it }, { request.getHeader(it) })
    }

    override fun hasResponse(): Boolean = appResponse != null
    override fun response(): ApplicationResponse {
        val currentResponse = appResponse
        if (currentResponse == null) {
            appResponse = Response()
            return appResponse!!
        } else
            throw IllegalStateException("Response already acquired for this request")
    }

    override fun response(body: ApplicationResponse.() -> Unit): ApplicationResponse {
        val r = response()
        r.body()
        return r
    }

    inner class Response : ApplicationResponse {
        override fun header(name: String, value: String): ApplicationResponse {
            response.setHeader(name, value)
            return this
        }

        override fun header(name: String, value: Int): ApplicationResponse {
            response.setIntHeader(name, value)
            return this
        }

        override fun status(code: Int): ApplicationResponse {
            response.setStatus(code)
            return this
        }

        override fun contentType(value: String): ApplicationResponse {
            response.setContentType(value)
            return this
        }

        override fun content(text: String, encoding: String): ApplicationResponse {
            response.setCharacterEncoding(encoding)
            val writer = response.getWriter()
            writer?.write(text)
            return this
        }

        override fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse {
            val writer = response.getWriter()
            writer.streamer()
            return this
        }

        override fun content(bytes: ByteArray): ApplicationResponse {
            val writer = response.getOutputStream()
            writer?.write(bytes)
            return this
        }

        override fun send() {
            response.flushBuffer()
        }

        override fun sendRedirect(url: String) {
            response.sendRedirect(url)
            response.flushBuffer()
        }

    }
}