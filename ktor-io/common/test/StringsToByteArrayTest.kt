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
        assertContentEquals(text.encodeToByteArray(), text.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `multibyte and surrogate pair text encodes to utf8`() {
        val text = "héllo wörld é东京 🚀 tail"
        assertContentEquals(text.encodeToByteArray(), text.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `surrogate pair at end of string encodes`() {
        val text = "rocket 🚀"
        assertContentEquals(text.encodeToByteArray(), text.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `unpaired high surrogate throws`() {
        assertFailsWith<Exception> { "abc\ud800def".toByteArray(Charsets.UTF_8) }
    }

    @Test
    fun `unpaired low surrogate throws`() {
        assertFailsWith<Exception> { "abc\ude80def".toByteArray(Charsets.UTF_8) }
    }

    @Test
    fun `high surrogate at end of string throws`() {
        assertFailsWith<Exception> { "abc\ud800".toByteArray(Charsets.UTF_8) }
    }

    @Test
    fun `empty string encodes to empty array`() {
        assertContentEquals(ByteArray(0), "".toByteArray(Charsets.UTF_8))
    }
}
