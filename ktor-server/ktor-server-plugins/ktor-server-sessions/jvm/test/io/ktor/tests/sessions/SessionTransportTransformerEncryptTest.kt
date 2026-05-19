/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.sessions.*
import kotlin.test.*

class SessionTransportTransformerEncryptTest {

    @Test
    fun `accepts 32-byte AES-256 encryption key`() {
        // AES-CBC requires a 16-byte IV regardless of key length. Constructing the transformer
        // with a 32-byte key used to fail with InvalidAlgorithmParameterException because the
        // IV was generated using the key size instead of the cipher block size.
        SessionTransportTransformerEncrypt(ByteArray(32), ByteArray(32))
    }

    @Test
    fun `transformWrite generates 16-byte IV for AES-256`() {
        val transformer = SessionTransportTransformerEncrypt(ByteArray(32), ByteArray(32))
        val encoded = transformer.transformWrite("hello")
        val iv = encoded.substringBefore('/').hexToByteArray()
        assertEquals(16, iv.size)
    }

    @Test
    fun `round-trip preserves payload for AES-128, AES-192 and AES-256`() {
        for (keyLength in intArrayOf(16, 24, 32)) {
            val transformer = SessionTransportTransformerEncrypt(ByteArray(keyLength), ByteArray(32))
            val original = "session-value-$keyLength"
            val encoded = transformer.transformWrite(original)
            val decoded = transformer.transformRead(encoded)
            assertEquals(original, decoded, "round-trip failed for AES-${keyLength * 8}")
        }
    }
}
