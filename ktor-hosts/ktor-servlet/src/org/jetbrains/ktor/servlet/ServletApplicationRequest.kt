package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.http.*

class ServletApplicationRequest(val call: ServletApplicationCall, val servletRequest: HttpServletRequest) : ApplicationRequest {
    override val requestLine: HttpRequestLine by lazy {
        val uri = servletRequest.requestURI
        val query = servletRequest.queryString
        HttpRequestLine(HttpMethod.parse(servletRequest.method),
                if (query == null) uri else "$uri?$query",
                servletRequest.protocol)
    }

    override val parameters: ValuesMap by lazy {
        val parametersMap = servletRequest.parameterMap ?: return@lazy ValuesMap.Empty
        ValuesMapBuilder(size = parametersMap.size).apply {
            for ((key, values) in parametersMap) {
                if (values != null) {
                    appendAll(key, values.asList())
                }
            }
        }.build()
    }

    override val headers: ValuesMap by lazy {
        ValuesMap.build(caseInsensitiveKey = true) {
            servletRequest.headerNames.asSequence().forEach {
                appendAll(it, servletRequest.getHeaders(it).toList())
            }
        }
    }

    private val servletReadChannel by lazy {
        val providedChannel = call.attributes.getOrNull(BaseApplicationCall.RequestChannelOverride)

        if (providedChannel == null) {
            call.ensureAsync()
            ServletReadChannel(servletRequest.inputStream)
        } else providedChannel
    }

    override val content: RequestContent = object : RequestContent(this) {
        override fun getMultiPartData(): MultiPartData = ServletMultiPartData(this@ServletApplicationRequest, servletRequest)
        override fun getInputStream(): InputStream = servletRequest.inputStream
        override fun getReadChannel(): ReadChannel = servletReadChannel
    }

    override val cookies: RequestCookies = ServletRequestCookies(servletRequest, this)
}

private class ServletRequestCookies(val servletRequest: HttpServletRequest, request: ApplicationRequest) : RequestCookies(request) {
    override val parsedRawCookies: Map<String, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { servletRequest.cookies?.associateBy({ it.name }, { it.value }) ?: emptyMap() }
}
