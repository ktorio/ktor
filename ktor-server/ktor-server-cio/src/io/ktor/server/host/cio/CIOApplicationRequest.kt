package io.ktor.server.host.cio

import kotlinx.coroutines.experimental.io.*
import io.ktor.application.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.http.HttpMethod
import io.ktor.http.cio.*
import io.ktor.http.request.parseQueryString
import io.ktor.request.*
import io.ktor.util.*

class CIOApplicationRequest(call: ApplicationCall,
                            private val input: ByteReadChannel,
                            private val request: Request) : BaseApplicationRequest(call) {
    override val cookies: RequestCookies by lazy { RequestCookies(this) }
    override fun receiveContent() = CIOIncomingContent(input, request.headers, this)
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
            get() = HttpMethod.parse(request.method.value.toString())

        override val remoteHost: String
            get() = "unknown" // TODO
    } }
}