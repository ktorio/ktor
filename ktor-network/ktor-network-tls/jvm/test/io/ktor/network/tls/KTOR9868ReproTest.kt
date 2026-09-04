/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.test.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Reproducer for KTOR-9868.
 *
 * A peer that sends a byte which is not a known TLS record type makes [readTLSRecord] fail with
 * `IllegalArgumentException: Invalid TLS record type code: <code>` thrown by [TLSRecordType.byCode].
 * Malformed peer input is an IO/protocol condition, so it should surface as a [TlsException] (an
 * [IOException] on JVM), the way the neighbouring frame-size check in the very same function already
 * does. As an [IllegalArgumentException] it escapes every `catch (cause: IOException)` a caller may
 * have, which is how the reporter got an uncaught crash in production.
 */
class KTOR9868ReproTest {

    /**
     * Record type `75` (`0x4B`) is the exact code from the reported stack trace.
     */
    @Test
    fun `unknown record type code is reported as an IO failure`() = runTest {
        val malformed = ByteReadChannel(byteArrayOf(75, 0x03, 0x03, 0x00, 0x00))

        assertFailsWith<IOException> {
            malformed.readTLSRecord()
        }
    }

    /**
     * The nearest working case: a well-formed record still parses.
     */
    @Test
    fun `well-formed handshake record is parsed`() = runTest {
        val record = ByteReadChannel(
            byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x04, 1, 2, 3, 4)
        ).readTLSRecord()

        assertEquals(TLSRecordType.Handshake, record.type)
        assertEquals(TLSVersion.TLS12, record.version)
        assertEquals(4L, record.packet.remaining)
    }

    /**
     * Control for the expected contract: the frame-size check in the same function already reports
     * malformed input as a [TlsException].
     */
    @Test
    fun `oversized frame is reported as an IO failure`() = runTest {
        val oversized = ByteReadChannel(byteArrayOf(0x16, 0x03, 0x03, 0xFF.toByte(), 0xFF.toByte()))

        assertFailsWith<IOException> {
            oversized.readTLSRecord()
        }
    }

    /**
     * The version occupies the same 5-byte record header and is read by the same function, so an
     * unknown version code has to be reported the same way as an unknown record type.
     */
    @Test
    fun `unknown record version code is reported as an IO failure`() = runTest {
        val malformed = ByteReadChannel(byteArrayOf(0x16, 0x09, 0x09, 0x00, 0x00))

        assertFailsWith<IOException> {
            malformed.readTLSRecord()
        }
    }
}
