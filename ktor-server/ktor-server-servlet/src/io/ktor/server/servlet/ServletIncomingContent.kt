package io.ktor.server.servlet

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import kotlinx.coroutines.experimental.*

@Deprecated("IncomingContent is no longer supported", level = DeprecationLevel.ERROR)
abstract class ServletIncomingContent(
    protected val request: ServletApplicationRequest
) : @Suppress("DEPRECATION_ERROR") IncomingContent {
    override val headers: Headers = request.headers
    override fun multiPartData(): MultiPartData = CIOMultipartDataBase(
            Unconfined,
            readChannel(),
            request.headers[HttpHeaders.ContentType]!!,
            request.headers[HttpHeaders.ContentLength]?.toLong()
            )
}
