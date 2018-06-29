package io.ktor.server.netty

import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.atomic.*

@Deprecated("IncomingContent is no longer supported", level = DeprecationLevel.ERROR)
class NettyHttpIncomingContent internal constructor(
        val request: NettyApplicationRequest
) : @Suppress("DEPRECATION_ERROR") IncomingContent {

    override val headers: Headers = request.headers

    override fun readChannel(): ByteReadChannel = throw UnsupportedOperationException("IncomingContent is no longer supported")

    override fun multiPartData(): MultiPartData = throw UnsupportedOperationException("IncomingContent is no longer supported")
}