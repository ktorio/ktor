package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*

internal class CIOHttpResponse(
    request: HttpRequest,
    override val requestTime: GMTDate,
    override val content: ByteReadChannel,
    private val response: Response,
    private val pipelined: Boolean
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

    override val responseTime: GMTDate = GMTDate()

    override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()

    override fun close() {
        if (pipelined) {
            runBlocking {
                val length = headers[HttpHeaders.ContentLength]!!.toLong()
                content.discard(length - content.totalBytesRead)
            }
        }
        response.release()
        executionContext.complete(Unit)
    }
}
