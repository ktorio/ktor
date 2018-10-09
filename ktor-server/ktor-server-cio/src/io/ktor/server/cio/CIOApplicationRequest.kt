package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.request.*
import io.ktor.server.engine.*
import kotlinx.coroutines.io.*

internal class CIOApplicationRequest(call: ApplicationCall,
                            private val input: ByteReadChannel,
                            private val request: Request) : BaseApplicationRequest(call) {
    override val cookies: RequestCookies by lazy(LazyThreadSafetyMode.NONE) { RequestCookies(this) }

    override fun receiveChannel() = input
    override val headers: Headers = CIOHeaders(request.headers)

    override val queryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        val uri = request.uri
        val qIdx = uri.indexOf('?')
        if (qIdx == -1 || qIdx == uri.lastIndex) return@lazy Parameters.Empty

        parseQueryString(uri.substring(qIdx + 1))
    }

    override val local: RequestConnectionPoint = object : RequestConnectionPoint {
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
            get() = HttpMethod.parse(request.method.value)

        override val remoteHost: String
            get() = "unknown" // TODO
    }

    internal fun release() {
        request.release()
    }
}
