/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.parseClientCookies
import io.ktor.http.parseClientCookiesHeader
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseClientCookiesHeaderTest {

    @Test
    fun `single cookie is parsed into one entry`() {
        assertEquals(listOf("name" to "value"), parseClientCookies("name=value"))
        assertEquals(mapOf("name" to "value"), parseClientCookiesHeader("name=value"))
    }

    @Test
    fun `multiple distinct cookies are preserved`() {
        val header = "a=1; b=2; c=3"
        assertEquals(
            listOf("a" to "1", "b" to "2", "c" to "3"),
            parseClientCookies(header)
        )
        assertEquals(mapOf("a" to "1", "b" to "2", "c" to "3"), parseClientCookiesHeader(header))
    }

    @Test
    fun `duplicate names are all preserved by list variant`() {
        assertEquals(
            listOf("name" to "value1", "name" to "value2"),
            parseClientCookies("name=value1; name=value2")
        )
    }

    @Test
    fun `duplicate names collapse to last value in map variant for backward compatibility`() {
        assertEquals(mapOf("name" to "value2"), parseClientCookiesHeader("name=value1; name=value2"))
    }

    @Test
    fun `quoted values are unwrapped`() {
        assertEquals(
            listOf("name" to "value with spaces"),
            parseClientCookies("name=\"value with spaces\"")
        )
    }

    @Test
    fun `escaped keys are skipped by default`() {
        val header = "name=value; \$x-enc=URI_ENCODING"
        assertEquals(listOf("name" to "value"), parseClientCookies(header))
    }

    @Test
    fun `escaped keys are preserved when skipEscaped is false`() {
        val header = "name=value; \$x-enc=URI_ENCODING"
        assertEquals(
            listOf("name" to "value", "\$x-enc" to "URI_ENCODING"),
            parseClientCookies(header, skipEscaped = false)
        )
    }

    @Test
    fun `empty header yields empty result`() {
        assertEquals(emptyList(), parseClientCookies(""))
        assertEquals(emptyMap(), parseClientCookiesHeader(""))
    }

    @Test
    fun `ordering of duplicate cookies is preserved`() {
        val header = "x=3; x=1; x=2"
        assertEquals(
            listOf("x" to "3", "x" to "1", "x" to "2"),
            parseClientCookies(header)
        )
    }
}
