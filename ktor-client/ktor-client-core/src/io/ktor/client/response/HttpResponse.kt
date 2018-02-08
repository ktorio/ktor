package io.ktor.client.response

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.charset.*
import java.util.*


interface HttpResponse : HttpMessage, Closeable {

    val call: HttpClientCall

    val status: HttpStatusCode

    val version: HttpProtocolVersion

    val requestTime: Date

    val responseTime: Date

    val executionContext: Job

    val content: ByteReadChannel
}

suspend fun HttpResponse.readText(
        charset: Charset? = null,
        pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): String {
    val length = headers[HttpHeaders.ContentLength]?.toInt() ?: 1
    return content.toByteArray(length, pool).toString(charset() ?: charset ?: Charsets.ISO_8859_1)
}
