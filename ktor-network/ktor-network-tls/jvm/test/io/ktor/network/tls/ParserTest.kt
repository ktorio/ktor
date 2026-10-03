/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlin.test.*

class ParserTest {

    @Test
    fun `unknown record type code is reported as TlsException`() = runTest {
        val malformed = ByteReadChannel(byteArrayOf(75, 0x03, 0x03, 0x00, 0x00))

        assertFailsWith<TlsException> {
            malformed.readTLSRecord()
        }
    }

    @Test
    fun `unknown record version code is reported as TlsException`() = runTest {
        val malformed = ByteReadChannel(byteArrayOf(0x16, 0x09, 0x09, 0x00, 0x00))

        assertFailsWith<TlsException> {
            malformed.readTLSRecord()
        }
    }

    @Test
    fun `oversized frame is reported as TlsException`() = runTest {
        val oversized = ByteReadChannel(byteArrayOf(0x16, 0x03, 0x03, 0xFF.toByte(), 0xFF.toByte()))

        assertFailsWith<TlsException> {
            oversized.readTLSRecord()
        }
    }
}
