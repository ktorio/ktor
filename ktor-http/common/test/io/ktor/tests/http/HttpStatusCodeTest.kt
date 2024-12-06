/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class HttpStatusCodeTest {
    @Test
    fun httpStatusCodeAll() {
        assertEquals(53, HttpStatusCode.allStatusCodes.size)
    }

    @Test
    fun httpStatusCodeFromValue() {
        assertEquals(HttpStatusCode.NotFound, HttpStatusCode.fromValue(404))
    }

    @Test
    fun httpStatusCodeConstructed() {
        assertEquals(HttpStatusCode.NotFound, HttpStatusCode(404, "Not Found"))
    }

    @Test
    fun httpStatusCodeWithDescription() {
        assertEquals(HttpStatusCode.NotFound, HttpStatusCode.NotFound.description("Missing Resource"))
    }

    @Test
    fun httpStatusCodeToString() {
        assertEquals("404 Not Found", HttpStatusCode.NotFound.toString())
    }
}
