package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import java.net.*
import kotlin.coroutines.*

@Suppress("KDocMissingDocumentation")
class AndroidHttpResponse(
    override val call: HttpClientCall,
    override val content: ByteReadChannel,
    override val headers: Headers,
    override val requestTime: GMTDate,
    override val responseTime: GMTDate,
    override val status: HttpStatusCode,
    override val version: HttpProtocolVersion,
    override val coroutineContext: CoroutineContext,
    private val connection: HttpURLConnection
) : HttpResponse {

    override fun close() {
        super.close()

        coroutineContext[Job]?.invokeOnCompletion {
            connection.disconnect()
        }
    }
}
