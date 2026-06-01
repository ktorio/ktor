/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.http.*
import io.ktor.server.netty.http.*
import io.netty.handler.codec.http3.*
import kotlin.test.*

class NettyHttp3ApplicationResponseTest {

    @Test
    fun `test all headers skip pseudo headers`() {
        val nettyHeaders = DefaultHttp3Headers()
        val headers = HttpMultiplexedResponseHeaders(nettyHeaders)
        nettyHeaders.status("200")
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertEquals(headersOf(HttpHeaders.ContentType.lowercase(), "text/plain"), headers.allValues())
    }

    @Test
    fun `test get header skip pseudo headers`() {
        val nettyHeaders = DefaultHttp3Headers()
        val headers = HttpMultiplexedResponseHeaders(nettyHeaders)
        nettyHeaders.status("200")
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertEquals("text/plain", headers[HttpHeaders.ContentType.lowercase()])
        assertNull(headers[":status"])
    }

    @Test
    fun `test get header values skip pseudo headers`() {
        val nettyHeaders = DefaultHttp3Headers()
        val headers = HttpMultiplexedResponseHeaders(nettyHeaders)
        nettyHeaders.status("200")
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertEquals(listOf("text/plain"), headers.values(HttpHeaders.ContentType.lowercase()))
        assertEquals(emptyList(), headers.values(":status"))
    }

    @Test
    fun `test append throws on pseudo headers`() {
        val nettyHeaders = DefaultHttp3Headers()
        val headers = HttpMultiplexedResponseHeaders(nettyHeaders)
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertFailsWith<IllegalHeaderNameException> { headers.append(":status", "200") }
    }
}
