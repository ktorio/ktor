package io.ktor.server.testing

import io.ktor.http.*
import kotlin.test.*

/**
 * [on] function receiver object
 */
object On

/**
 * [it] function receiver object
 */
object It

/**
 * DSL for creating a test case
 */
@Suppress("UNUSED_PARAMETER")
fun on(comment: String, body: On.() -> Unit) = On.body()

/**
 * DSL function for test test case assertions
 */
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

/**
 * Return parsed content type from the test response
 */
fun TestApplicationResponse.contentType(): ContentType {
    val contentTypeHeader = requireNotNull(headers[HttpHeaders.ContentType])
    return ContentType.parse(contentTypeHeader)
}
