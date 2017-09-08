package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

class CIOApplicationRequest(call: ApplicationCall,
                            private val input: ByteReadChannel,
                            private val multipart: ReceiveChannel<MultipartEvent>,
                            private val request: Request) : BaseApplicationRequest(call) {
    override val cookies: RequestCookies by lazy { RequestCookies(this) }
    override fun receiveContent() = CIOIncomingContent(input, multipart, this)
    override val headers: ValuesMap = CIOHeaders(request.headers)

    override val queryParameters: ValuesMap by lazy {
        val uri = request.uri
        val qIdx = uri.indexOf('?')
        if (qIdx == -1 || qIdx == uri.lastIndex) return@lazy ValuesMap.Empty

        parseQueryString(uri.substring(qIdx + 1))
    }

    override val local: RequestConnectionPoint by lazy { object : RequestConnectionPoint {
        override val scheme: String
            get() = "http"

        override val version: String
            get() = request.version.toString()

        override val uri: String
            get() = request.uri.toString()

        override val host: String
            get() = request.headers["Host"]?.toString()?.substringBefore(":") ?: "localhost"

        override val port: Int
            get() = request.headers["Host"]?.toString()?.substringAfter(":", "80")?.toInt() ?: 80

        override val method: HttpMethod
            get() = HttpMethod.parse(request.method.name.toString())

        override val remoteHost: String
            get() = "unknown" // TODO
    } }
}