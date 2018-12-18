package io.ktor.client.engine.urlfetch

import io.ktor.client.call.HttpClientCall
import io.ktor.client.response.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext

internal class UrlFetchResponse(
    override val status: HttpStatusCode,
    override val call: HttpClientCall,
    override val requestTime: GMTDate,
    override val coroutineContext: CoroutineContext,
    override val content: ByteReadChannel,
    override val headers: Headers
) : HttpResponse {

    override val version = HttpProtocolVersion.HTTP_1_1

    override val responseTime: GMTDate = GMTDate()

    override fun close() {}
}
