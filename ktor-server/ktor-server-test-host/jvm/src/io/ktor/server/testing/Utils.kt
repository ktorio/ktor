/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import org.junit.Assert.*

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

/**
 * Return parsed content type from the test response
 */
fun TestApplicationResponse.contentType(): ContentType {
    val contentTypeHeader = requireNotNull(headers[HttpHeaders.ContentType])
    return ContentType.parse(contentTypeHeader)
}
