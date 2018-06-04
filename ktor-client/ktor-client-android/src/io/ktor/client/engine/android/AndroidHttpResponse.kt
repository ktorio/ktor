package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.net.*
import java.util.*

class AndroidHttpResponse(
    override val call: HttpClientCall,
    override val content: ByteReadChannel,
    override val executionContext: Job,
    override val headers: Headers,
    /*override*/ val requestTime: Date,
    /*override*/ val responseTime: Date,
    override val status: HttpStatusCode,
    override val version: HttpProtocolVersion,
    private val connection: HttpURLConnection
) : HttpResponse {

    override fun close() {
        connection.disconnect()
    }
}