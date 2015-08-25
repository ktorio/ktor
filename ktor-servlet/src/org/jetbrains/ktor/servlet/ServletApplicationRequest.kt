package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.charset.*
import java.util.*
import javax.servlet.*
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

    override val attributes = Attributes()
}
