package io.ktor.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.http.request.*
import io.ktor.request.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.io.*
import java.nio.*
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

    class ServletIncomingContent(override val request: ServletApplicationRequest) : IncomingContent {
        private val copyJob by lazy { servletReader(request.servletRequest.inputStream) }
        private fun byteChannel() = copyJob.channel

        override fun readChannel(): ReadChannel = object: ReadChannel {
            suspend override fun read(dst: ByteBuffer): Int {
                return copyJob.channel.readAvailable(dst)
            }

            override fun close() {
                runBlocking {
                    copyJob.cancel()
                    copyJob.join()
                }
            }
        }

        override fun multiPartData(): MultiPartData = ServletMultiPartData(request, request.servletRequest)
        override fun inputStream(): InputStream = request.servletRequest.inputStream
    }
}

