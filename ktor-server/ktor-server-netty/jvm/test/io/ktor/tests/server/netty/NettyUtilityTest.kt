/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import kotlin.test.*

class NettyUtilityTest {

    @Test
    fun testTransferEncodingVerification() {
        assertTrue(listOf<String>().hasValidTransferEncoding())

        assertTrue(listOf("chunked").hasValidTransferEncoding())
        assertTrue(listOf("zchunked, chunked").hasValidTransferEncoding())
        assertTrue(listOf("zchunked", "chunked").hasValidTransferEncoding())
        assertTrue(listOf("chunkedz, chunked").hasValidTransferEncoding())
        assertTrue(listOf("chunkedz", "chunked").hasValidTransferEncoding())

        assertFalse(listOf("chunked,zchunked").hasValidTransferEncoding())
        assertFalse(listOf("chunked,chunkedz").hasValidTransferEncoding())
        assertFalse(listOf("chunked, zchunked").hasValidTransferEncoding())
        assertFalse(listOf("chunked", "zchunked").hasValidTransferEncoding())
        assertFalse(listOf("chunked, chunkedz").hasValidTransferEncoding())
        assertFalse(listOf("chunked", "chunkedz").hasValidTransferEncoding())

        assertFalse(listOf("chunked", "gzip").hasValidTransferEncoding())

        assertTrue(listOf("zchunked").hasValidTransferEncoding())
        assertTrue(listOf("chunkedz").hasValidTransferEncoding())
        assertTrue(listOf("zchunkedz").hasValidTransferEncoding())
    }
}
