/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.js.*
import kotlin.test.*

class TextDecoderFallbackTest {

    @Test
    fun testReplacement() {
        val origin = byteArrayOf(0x81.toByte(), 0x8F.toByte(), 0x90.toByte())

        val fatalDecoder = ISO8859TextDecoder.tryCreate("ISO-8859-1", fatal = true)
        assertNotNull(fatalDecoder)
        val decoder = ISO8859TextDecoder.tryCreate("ISO-8859-1", fatal = false)
        assertNotNull(decoder)

        assertFails {
            fatalDecoder.decode(origin)
        }

        val actual = decoder.decode(origin)
        assertEquals("\uFFFD".repeat(3), actual)
    }

    @Test
    fun testPrintableASCII() {
        val asciiArray = (0x20..0x7E).toList().map { it.toByte() }.toByteArray()

        val decoder = ISO8859TextDecoder.tryCreate("ISO-8859-1", fatal = false)
        assertNotNull(decoder)
        val decoded = decoder.decode(asciiArray)

        assertEquals(
            " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~",
            decoded
        )
    }
}
