package io.ktor.server.testing

import io.ktor.http.*
import kotlin.test.*

object On

object It

@Suppress("UNUSED_PARAMETER")
fun on(comment: String, body: On.() -> Unit) = On.body()

@Suppress("UNUSED_PARAMETER")
inline fun On.it(description: String, body: It.() -> Unit) = It.body()

internal suspend fun assertFailsSuspend(block: suspend () -> Unit): Throwable {
    var exception: Throwable? = null
    try {
        block()
    } catch (cause: Throwable) {
        exception = cause
    }

    assertNotNull(exception)
    return exception
}

fun TestApplicationResponse.contentType(): ContentType {
    val contentTypeHeader = requireNotNull(headers[HttpHeaders.ContentType])
    return ContentType.parse(contentTypeHeader)
}
