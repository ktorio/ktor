/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.js.*
import org.khronos.webgl.*
import kotlin.test.*

class TextDecoderFallbackTest {

    @Test
    fun testReplacement() {
        val origin = byteArrayOf(0x81.toByte(), 0x8F.toByte(), 0x90.toByte())
        val jsArray = origin.unsafeCast<Int8Array>()

        val fatalDecoder = TextDecoderFallback("ISO-8859-1", fatal = true)
        val decoder = TextDecoderFallback("ISO-8859-1", fatal = false)

        assertFails {
            fatalDecoder.decode(jsArray)
        }

        val actual = decoder.decode(jsArray)
        assertEquals("\uFFFD".repeat(3), actual)
    }

    @Test
    fun testPrintableASCII() {
        val asciiArray = (0x20..0x7E).toList().map { it.toByte() }.toByteArray()

        val decoder = TextDecoderFallback("ISO-8859-1", fatal = false)
        val decoded = decoder.decode(asciiArray.unsafeCast<Int8Array>())

        assertEquals(
            " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~",
            decoded
        )
    }
}
