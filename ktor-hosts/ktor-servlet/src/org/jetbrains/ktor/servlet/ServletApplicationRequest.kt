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

    override val actualRoute: RequestSocketRoute = object : RequestSocketRoute {
        override val scheme: String
            get() = servletRequest.scheme ?: "http"

        override val port: Int
            get() = servletRequest.serverPort

        override val host: String
            get() = servletRequest.serverName ?: "localhost"

        override val remoteHost: String
            get() = servletRequest.remoteHost
    }

    override val parameters: ValuesMap by lazy {
        object : ValuesMap {
            override fun getAll(name: String): List<String> = servletRequest.getParameterValues(name)?.asList() ?: emptyList()
            override fun entries(): Set<Map.Entry<String, List<String>>> {
                return servletRequest.parameterNames.asSequence().map {
                    object : Map.Entry<String, List<String>> {
                        override val key: String get() = it
                        override val value: List<String> get() = getAll(it)
                    }
                }.toSet()
            }

            override fun isEmpty(): Boolean = servletRequest.parameterNames.asSequence().none()
            override val caseInsensitiveKey: Boolean get() = false
            override fun names(): Set<String> = servletRequest.parameterNames.asSequence().toSet()
        }
    }

    override val headers: ValuesMap by lazy {
        object : ValuesMap {
            override fun getAll(name: String): List<String> = servletRequest.getHeaders(name)?.toList() ?: emptyList()
            override fun entries(): Set<Map.Entry<String, List<String>>> {
                return servletRequest.headerNames.asSequence().map {
                    object : Map.Entry<String, List<String>> {
                        override val key: String get() = it
                        override val value: List<String> get() = getAll(it)
                    }
                }.toSet()
            }

            override fun isEmpty(): Boolean = servletRequest.headerNames.asSequence().none()
            override val caseInsensitiveKey: Boolean get() = true
            override fun names(): Set<String> = servletRequest.headerNames.asSequence().toSet()
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
