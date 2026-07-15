/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class StringsToByteArrayTest {

    @Test
    fun `ascii text encodes to utf8`() {
        val text = """{"id":42,"name":"Jordan","active":true}"""
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text.length, bytes.size)
        assertEquals(text, bytes.decodeToString())
    }

    @Test
    fun `multibyte and surrogate pair text round trips`() {
        val text = "héllo wörld é东京 🚀 tail"
        assertEquals(text, text.toByteArray(Charsets.UTF_8).decodeToString())
    }

    @Test
    fun `surrogate pair at end of string round trips`() {
        val text = "rocket 🚀"
        assertEquals(text, text.toByteArray(Charsets.UTF_8).decodeToString())
    }

    @Test
    fun `unpaired surrogate is replaced and does not throw`() {
        // The replacement byte sequence is platform-specific ('?' on JVM via String.getBytes,
        // U+FFFD elsewhere); the contract is that encoding never throws and the
        // surrounding characters survive.
        val decoded = "abc\ud800def".toByteArray(Charsets.UTF_8).decodeToString()
        assertTrue(decoded.startsWith("abc"))
        assertTrue(decoded.endsWith("def"))
    }

    @Test
    fun `empty string encodes to empty array`() {
        assertContentEquals(ByteArray(0), "".toByteArray(Charsets.UTF_8))
    }
}
