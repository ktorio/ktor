/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class MimesTest {

    @Test
    fun `test mime with multiple extensions`() {
        val textPlain = "text/plain".toContentType()
        val textMime = "text" to textPlain
        val txtMime = "txt" to textPlain
        assertTrue(mimes.contains(textMime))
        assertTrue(mimes.contains(txtMime))
    }

    @Test
    fun `test mime with single extension`() {
        val acad = "application/acad".toContentType()
        val dwgMime = "dwg" to acad
        assertTrue(mimes.contains(dwgMime))
    }

    @Test
    fun `test mimes size matches preallocated list size`() {
        assertEquals(INITIAL_MIMES_LIST_SIZE, mimes.size)
    }
}
