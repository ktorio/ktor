package io.ktor.server.servlet

import io.ktor.content.*
import io.ktor.http.*
import java.io.*

abstract class ServletIncomingContent(
    protected val request: ServletApplicationRequest
) : IncomingContent {
    override val headers: Headers = request.headers
    override fun multiPartData(): MultiPartData = ServletMultiPartData(request)
    override fun inputStream(): InputStream = request.servletRequest.inputStream
}
