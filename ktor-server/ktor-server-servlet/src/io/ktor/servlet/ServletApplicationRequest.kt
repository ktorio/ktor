package io.ktor.servlet

import io.ktor.application.ApplicationCall
import io.ktor.cio.ReadChannel
import io.ktor.content.IncomingContent
import io.ktor.host.BaseApplicationRequest
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.request.parseQueryString
import io.ktor.request.MultiPartData
import io.ktor.request.RequestCookies
import io.ktor.util.ValuesMap
import kotlinx.coroutines.experimental.runBlocking
import java.io.InputStream
import java.nio.ByteBuffer
import javax.servlet.http.HttpServletRequest

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

