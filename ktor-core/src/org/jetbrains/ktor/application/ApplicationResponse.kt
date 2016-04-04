package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import java.io.*
import java.nio.charset.*

/**
 * Represents server's response
 */
interface ApplicationResponse {
    val headers: ResponseHeaders
    val cookies: ResponseCookies

    fun status(): HttpStatusCode?
    fun status(value: HttpStatusCode)
    fun interceptStatus(handler: (value: HttpStatusCode, next: (value: HttpStatusCode) -> Unit) -> Unit)

    fun stream(body: OutputStream.() -> Unit): Unit
    fun interceptStream(handler: (body: OutputStream.() -> Unit, next: (body: OutputStream.() -> Unit) -> Unit) -> Unit)
}

fun ApplicationResponse.streamBytes(bytes: ByteArray) {
    stream { write(bytes) }
}

fun ApplicationResponse.streamText(text: String, encoding: String = "UTF-8") {
    streamBytes(text.toByteArray(Charset.forName(encoding)))
}

fun ApplicationResponse.write(body: Writer.() -> Unit) {
    stream {
        writer().use { writer ->
            writer.body()
        }
    }
}