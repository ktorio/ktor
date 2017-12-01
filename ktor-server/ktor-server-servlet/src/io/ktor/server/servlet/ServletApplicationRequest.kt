package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.*
import java.nio.ByteBuffer
import javax.servlet.http.*

class ServletApplicationRequest(call: ApplicationCall,
                                val servletRequest: HttpServletRequest) : BaseApplicationRequest(call) {

    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters by lazy {
        servletRequest.queryString?.let { parseQueryString(it) } ?: ValuesMap.Empty
    }

    override val headers: ValuesMap = ServletApplicationRequestHeaders(servletRequest)
    override val cookies: RequestCookies = ServletApplicationRequestCookies(servletRequest, this)

    override fun receiveContent() = ServletIncomingContent(this)

    class ServletIncomingContent(
            private val request: ServletApplicationRequest
    ) : IncomingContent {
        override val headers: Headers = request.headers

        private val copyJob by lazy { servletReader(request.servletRequest.inputStream) }

        override fun readChannel(): ByteReadChannel = copyJob.channel

        override fun multiPartData(): MultiPartData = ServletMultiPartData(request, request.servletRequest)
        override fun inputStream(): InputStream = request.servletRequest.inputStream
    }
}

