package ktor.application

import javax.servlet.http.*
import ktor.application.*
import java.util.*

public class ServletApplicationRequest(override val application: Application, val request: HttpServletRequest, val response: HttpServletResponse) : ApplicationRequest {
    override val uri: String = request.getRequestURI()
    override val httpMethod: String = request.getMethod()

    var appResponse: Response? = null

    override val parameters: Map<String, List<String>>
    init {
        val result = HashMap<String, MutableList<String>>()
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
        parameters = result
    }

    override fun header(name: String): String? = request.getHeader(name)

    override fun hasResponse(): Boolean = appResponse != null
    override fun response(): ApplicationResponse {
        val currentResponse = appResponse
        if (currentResponse == null) {
            appResponse = Response()
            return appResponse!!
        } else
            throw IllegalStateException("Response already aquired for this request")
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