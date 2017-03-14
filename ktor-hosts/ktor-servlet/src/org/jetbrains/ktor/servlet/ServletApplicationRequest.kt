package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.http.*

class ServletApplicationRequest(val servletRequest: HttpServletRequest, requestChannelOverride: () -> ReadChannel?) : BaseApplicationRequest() {
    override val attributes = Attributes()
    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters by lazy {
        servletRequest.queryString?.let { parseQueryString(it) } ?: ValuesMap.Empty
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

    private val servletReadChannel = lazy {
        requestChannelOverride() ?: run {
            ServletReadChannel(servletRequest.inputStream)
        }
    }

    override fun getReadChannel(): ReadChannel = servletReadChannel.value
    override fun getMultiPartData(): MultiPartData = ServletMultiPartData(this@ServletApplicationRequest, servletRequest)
    override fun getInputStream(): InputStream = servletRequest.inputStream

    override val cookies: RequestCookies = ServletRequestCookies(servletRequest, this)

    fun close() {
        if (servletReadChannel.isInitialized()) {
            servletReadChannel.value.close()
        }
    }
}

private class ServletRequestCookies(val servletRequest: HttpServletRequest, request: ApplicationRequest) : RequestCookies(request) {
    override val parsedRawCookies: Map<String, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { servletRequest.cookies?.associateBy({ it.name }, { it.value }) ?: emptyMap() }
}
