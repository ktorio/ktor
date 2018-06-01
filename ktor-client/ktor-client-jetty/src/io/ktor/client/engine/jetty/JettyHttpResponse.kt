package io.ktor.client.engine.jetty

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.util.*

internal class JettyHttpResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    /*override*/ val requestTime: Date,
    override val executionContext: CompletableDeferred<Unit>,
    override val content: ByteReadChannel,
    private val origin: Closeable
) : HttpResponse {
    override val version = HttpProtocolVersion.HTTP_2_0
    /*override*/ val responseTime = Date()

    override fun close() {
        origin.close()
        executionContext.complete(Unit)
    }
}