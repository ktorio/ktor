/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DigestTest {

    @Test
    fun md5Rfc1321TestVectors() = runTest {
        // Test vectors from RFC 1321, Appendix A.5
        val vectors = listOf(
            "" to "d41d8cd98f00b204e9800998ecf8427e",
            "a" to "0cc175b9c0f1b6a831c399e269772661",
            "abc" to "900150983cd24fb0d6963f7d28e17f72",
            "message digest" to "f96b697d7cb7938d525a2f31aaf161d0",
            "abcdefghijklmnopqrstuvwxyz" to "c3fcd3d76192e4007dfb496cca67e13b",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                to "d174ab98d277d9f5a5611c2c9f419d9f",
            "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                to "57edf4a22be3c955ac49da2e2107b67a",
            // Padding boundary: 55 bytes fit in one block, 56 bytes force a second block
            "a".repeat(55) to "ef1772b6dff9a122358552954ad0df65",
            "a".repeat(56) to "3b0c8ac703f828b04c6c197006d17218",
            "a".repeat(63) to "b06521f39153d618550606be297466d5",
            "a".repeat(64) to "014842d480b571495a4a0363793f7367",
            "a".repeat(128) to "e510683b3f5ffe4093d021808bc6ff70",
            // Well-known values
            "\u0000" to "93b885adfe0da089cdf634904fd59f71",
            "The quick brown fox jumps over the lazy dog" to "9e107d9d372bb6826bd81d3542a419d6",
        )

        for ((input, expected) in vectors) {
            val digestView = org.khronos.webgl.DataView(md5(input.encodeToByteArray()))
            val actual = hex(ByteArray(digestView.byteLength) { digestView.getUint8(it) })
            assertEquals(expected, actual, "MD5(\"$input\")")
        }
    }
}
