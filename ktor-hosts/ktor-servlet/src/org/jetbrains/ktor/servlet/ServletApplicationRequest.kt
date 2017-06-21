package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.*
import javax.servlet.http.*
import kotlin.collections.ArrayList

class ServletApplicationRequest(override val call: ServletApplicationCall,
                                val servletRequest: HttpServletRequest,
                                requestChannelOverride: () -> ReadChannel?) : BaseApplicationRequest() {
    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters by lazy {
        servletRequest.queryString?.let { parseQueryString(it) } ?: ValuesMap.Empty
    }

    override val headers: ValuesMap = ServletHeadersValuesMap(servletRequest)

    class ServletHeadersValuesMap(val servletRequest: HttpServletRequest) : ValuesMap {
        override fun getAll(name: String): List<String> {
            val headersEnumeration = servletRequest.getHeaders(name) ?: return emptyList()
            if (!headersEnumeration.hasMoreElements()) return emptyList()

            val first = headersEnumeration.nextElement()
            if (!headersEnumeration.hasMoreElements()) return Collections.singletonList(first)

            val result = ArrayList<String>(2)
            result.add(first)

            while (headersEnumeration.hasMoreElements()) {
                result.add(headersEnumeration.nextElement())
            }

            return result
        }

        override fun get(name: String): String? = servletRequest.getHeader(name)

        override fun contains(name: String): Boolean = servletRequest.getHeader(name) != null

        override fun forEach(body: (String, List<String>) -> Unit) {
            val namesEnumeration = servletRequest.headerNames ?: return
            while (namesEnumeration.hasMoreElements()) {
                val name = namesEnumeration.nextElement()
                val headersEnumeration = servletRequest.getHeaders(name) ?: continue
                val values = headersEnumeration.asSequence().toList()
                body(name, values)
            }
        }

        override fun entries(): Set<Map.Entry<String, List<String>>> {
            val names = servletRequest.headerNames
            val set = LinkedHashSet<Map.Entry<String, List<String>>>()
            while (names.hasMoreElements()) {
                val name = names.nextElement()
                val entry = object : Map.Entry<String, List<String>> {
                    override val key: String get() = name
                    override val value: List<String> get() = getAll(name)
                }
                set.add(entry)
            }
            return set
        }

        override fun isEmpty(): Boolean = !servletRequest.headerNames.hasMoreElements()
        override val caseInsensitiveKey: Boolean get() = true
        override fun names(): Set<String> = servletRequest.headerNames.asSequence().toSet()
    }

    private val servletReadChannel = lazy {
        requestChannelOverride() ?: run {
            ServletReadChannel(servletRequest.inputStream)
        }
    }

    override fun receiveContent() = ServletIncomingContent(this)

    class ServletIncomingContent(override val request: ServletApplicationRequest) : IncomingContent {
        override fun readChannel(): ReadChannel = request.servletReadChannel.value
        override fun multiPartData(): MultiPartData = ServletMultiPartData(request, request.servletRequest)
        override fun inputStream(): InputStream = request.servletRequest.inputStream
    }

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
