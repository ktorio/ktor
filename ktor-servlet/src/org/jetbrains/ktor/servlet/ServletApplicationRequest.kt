package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.nio.charset.*
import javax.servlet.http.*

public class ServletApplicationRequest(private val servletRequest: HttpServletRequest) : ApplicationRequest {
    override val requestLine: HttpRequestLine by lazy {
        val uri = servletRequest.requestURI
        val query = servletRequest.queryString
        HttpRequestLine(HttpMethod.parse(servletRequest.method),
                        if (query == null) uri else "$uri?$query",
                        servletRequest.protocol)
    }

    override val body: String
        get() {
            val charsetName = contentType().parameter("charset")
            val charset = charsetName?.let { Charset.forName(it) } ?: Charsets.ISO_8859_1
            return servletRequest.inputStream.reader(charset).readText()
        }

    override val parameters: ValuesMap by lazy {
        ValuesMap.build {
            val parametersMap = servletRequest.parameterMap
            if (parametersMap != null) {
                for ((key, values) in parametersMap) {
                    if (values != null) {
                        appendAll(key, values.asList())
                    }
                }
            }
        }
    }

    override val headers: ValuesMap by lazy {
        ValuesMap.build {
            servletRequest.headerNames.asSequence().forEach {
                appendAll(it, servletRequest.getHeaders(it).toList())
            }
        }
    }

    override val attributes = Attributes()
}
