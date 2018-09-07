package io.ktor.client.engine.js

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.Headers
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.w3c.fetch.*
import kotlin.coroutines.*

class JsHttpResponse(
    override val call: HttpClientCall,
    override val requestTime: GMTDate,
    private val response: Response,
    override val content: ByteReadChannel,
    override val coroutineContext: CoroutineContext
) : HttpResponse {

    override val status: HttpStatusCode = HttpStatusCode.fromValue(response.status.toInt())

    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1

    override val responseTime: GMTDate = GMTDate()

    override val headers: Headers = Headers.build {
        response.headers.asDynamic().forEach { value: String, key: String ->
            append(key, value)
        }

        Unit
    }

    override fun close() {
        @Suppress("UNCHECKED_CAST")
        (coroutineContext[Job] as CompletableDeferred<Unit>).complete(Unit)
    }
}
