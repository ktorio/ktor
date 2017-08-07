package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.http.*

class ServletApplicationRequest(call: ApplicationCall,
                                val servletRequest: HttpServletRequest) : BaseApplicationRequest(call) {

    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters by lazy {
        servletRequest.queryString?.let { parseQueryString(it) } ?: ValuesMap.Empty
    }

    override val headers: ValuesMap = ServletApplicationRequestHeaders(servletRequest)
    override val cookies: RequestCookies = ServletApplicationRequestCookies(servletRequest, this)

    private val servletReadChannel = lazy { ServletReadChannel(servletRequest.inputStream) }

    override fun receiveContent() = ServletIncomingContent(this)

    class ServletIncomingContent(override val request: ServletApplicationRequest) : IncomingContent {
        override fun readChannel(): ReadChannel = request.servletReadChannel.value
        override fun multiPartData(): MultiPartData = ServletMultiPartData(request, request.servletRequest)
        override fun inputStream(): InputStream = request.servletRequest.inputStream
    }

    fun close() {
        if (servletReadChannel.isInitialized()) {
            servletReadChannel.value.close()
        }
    }
}

