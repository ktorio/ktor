/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.http.*
import io.ktor.server.netty.http2.*
import io.netty.handler.codec.http2.*
import kotlin.test.*

class NettyHttp2ApplicationResponseTest {

    @Test
    fun testAllHeadersSkipPseudoHeaders() {
        val nettyHeaders = DefaultHttp2Headers()
        val headers = NettyHttp2ApplicationResponse.Http2ResponseHeaders(nettyHeaders)
        nettyHeaders.status("200")
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertEquals(headersOf(HttpHeaders.ContentType.lowercase(), "text/plain"), headers.allValues())
    }

    @Test
    fun testGetHeaderSkipPseudoHeaders() {
        val nettyHeaders = DefaultHttp2Headers()
        val headers = NettyHttp2ApplicationResponse.Http2ResponseHeaders(nettyHeaders)
        nettyHeaders.status("200")
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertEquals("text/plain", headers[HttpHeaders.ContentType.lowercase()])
        assertNull(headers[":status"])
    }

    @Test
    fun testGetHeaderValuesSkipPseudoHeaders() {
        val nettyHeaders = DefaultHttp2Headers()
        val headers = NettyHttp2ApplicationResponse.Http2ResponseHeaders(nettyHeaders)
        nettyHeaders.status("200")
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertEquals(listOf("text/plain"), headers.values(HttpHeaders.ContentType.lowercase()))
        assertEquals(emptyList(), headers.values(":status"))
    }

    @Test
    fun testAppendThrowsOnPseudoHeaders() {
        val nettyHeaders = DefaultHttp2Headers()
        val headers = NettyHttp2ApplicationResponse.Http2ResponseHeaders(nettyHeaders)
        headers.append(HttpHeaders.ContentType, "text/plain")
        assertFailsWith<IllegalHeaderNameException> { headers.append(":status", "200") }
    }
}
