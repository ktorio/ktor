package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.http.*

internal class ServletApplicationRequest(private val servletRequest: HttpServletRequest) : ApplicationRequest {
    override val requestLine: HttpRequestLine by lazy {
        val uri = servletRequest.requestURI
        val query = servletRequest.queryString
        HttpRequestLine(HttpMethod.parse(servletRequest.method),
                if (query == null) uri else "$uri?$query",
                servletRequest.protocol)
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
        ValuesMap.build(caseInsensitiveKey = true) {
            servletRequest.headerNames.asSequence().forEach {
                appendAll(it, servletRequest.getHeaders(it).toList())
            }
        }
    }

    override val content: ApplicationRequestContent = object : ApplicationRequestContent(this) {
        override fun getMultiPartData(): MultiPartData = ServletMultiPartData(this@ServletApplicationRequest, servletRequest)
        override fun getInputStream(): InputStream = servletRequest.inputStream
    }

    override val cookies : RequestCookies = ServletRequestCookies(servletRequest, this)
}

private class ServletRequestCookies(val servletRequest: HttpServletRequest, request: ApplicationRequest) : RequestCookies(request) {
    override val parsedRawCookies: Map<String, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { servletRequest.cookies?.toMap({ it.name }, { it.value }) ?: emptyMap() }
}
