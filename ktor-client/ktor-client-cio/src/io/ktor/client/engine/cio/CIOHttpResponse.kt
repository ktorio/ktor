package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.*

class CIOHttpResponse(
        request: HttpRequest,
        override val requestTime: Date,
        override val content: ByteReadChannel,
        private val response: Response
) : HttpResponse {
    override val call: HttpClientCall = request.call
    override val status: HttpStatusCode = HttpStatusCode.fromValue(response.status)
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
    override val headers: Headers = Headers.build {
        val origin = CIOHeaders(response.headers)
        origin.names().forEach {
            appendAll(it, origin.getAll(it))
        }
    }

    override val responseTime: Date = Date()

    override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()

    override fun close() {
        response.release()
        executionContext.complete(Unit)
    }
}
