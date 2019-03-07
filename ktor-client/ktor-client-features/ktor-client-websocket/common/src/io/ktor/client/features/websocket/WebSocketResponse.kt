package io.ktor.client.features.websocket

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

/**
 * Response produced by [WebSocketEngine].
 *
 * @property session - connection [WebSocketSession].
 */
class WebSocketResponse(
    override val coroutineContext: CoroutineContext,
    override val requestTime: GMTDate,
    val session: WebSocketSession,
    override val headers: Headers = Headers.Empty,
    override val status: HttpStatusCode = HttpStatusCode.SwitchingProtocols,
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
) : HttpResponse {

    override lateinit var call: HttpClientCall
        internal set

    override val responseTime: GMTDate = GMTDate()

    override val content: ByteReadChannel
        get() = throw WebSocketException(
            "Bytes from [content] is not available in [WebSocketResponse]. Consider using [session] instead."
        )
}
