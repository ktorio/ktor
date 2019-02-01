package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

internal class CIOHttpResponse(
    request: HttpRequest,
    override val headers: Headers,
    override val requestTime: GMTDate,
    override val content: ByteReadChannel,
    response: Response,
    override val coroutineContext: CoroutineContext
) : HttpResponse {

    override val call: HttpClientCall = request.call

    override val status: HttpStatusCode = HttpStatusCode(response.status, response.statusText.toString())

    override val version: HttpProtocolVersion = HttpProtocolVersion.parse(response.version)

    override val responseTime: GMTDate = GMTDate()

    override fun close() {
        super.close()
        content.cancel()
    }
}
