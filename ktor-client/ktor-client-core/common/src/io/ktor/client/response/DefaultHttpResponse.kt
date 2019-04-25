package io.ktor.client.response

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

/**
 * Default [HttpResponse] implementation.
 */
@InternalAPI
class DefaultHttpResponse(override val call: HttpClientCall, responseData: HttpResponseData) : HttpResponse() {
    override val coroutineContext: CoroutineContext = responseData.callContext

    override val status: HttpStatusCode = responseData.statusCode

    override val version: HttpProtocolVersion = responseData.version

    override val requestTime: GMTDate = responseData.requestTime

    override val responseTime: GMTDate = responseData.responseTime

    override val content: ByteReadChannel = responseData.body as? ByteReadChannel ?: ByteReadChannel.Empty

    override val headers: Headers = responseData.headers
}
