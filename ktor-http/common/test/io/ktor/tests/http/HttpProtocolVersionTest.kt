/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class HttpProtocolVersionTest {

    @Test
    fun `parse returns cached HTTP 1_1 instance`() {
        assertSame(HttpProtocolVersion.HTTP_1_1, HttpProtocolVersion.parse("HTTP/1.1"))
    }

    @Test
    fun `parse returns cached HTTP 1_0 instance`() {
        assertSame(HttpProtocolVersion.HTTP_1_0, HttpProtocolVersion.parse("HTTP/1.0"))
    }

    @Test
    fun `parse returns cached HTTP 2_0 instance`() {
        assertSame(HttpProtocolVersion.HTTP_2_0, HttpProtocolVersion.parse("HTTP/2.0"))
    }

    @Test
    fun `parse returns cached HTTP 3_0 instance`() {
        assertSame(HttpProtocolVersion.HTTP_3_0, HttpProtocolVersion.parse("HTTP/3.0"))
    }

    @Test
    fun `parse handles unknown protocol via slow path`() {
        val spdy = HttpProtocolVersion.parse("SPDY/3.0")
        assertEquals("SPDY", spdy.name)
        assertEquals(3, spdy.major)
        assertEquals(0, spdy.minor)
    }

    @Test
    fun `parse fails for invalid format`() {
        assertFails {
            HttpProtocolVersion.parse("INVALID")
        }
    }
}
