/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import kotlin.test.*

class URIEncodingCookiesTest {
    @Test
    fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    @Test
    fun `space inside`() {
        testEncode("abc+123", "abc 123")
    }

    @Test
    fun `equals inside`() {
        testEncode("abc%3D123", "abc=123")
    }

    @Test
    fun `encode keep digits`() {
        testEncode("0123456789", "0123456789")
    }

    @Test
    fun `encode keep letters`() {
        testEncode(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        )
    }

    @Test
    fun `encode keep hypen`() {
        testEncode("abc-123", "abc-123")
    }

    @Test
    fun `encode keep underscore`() {
        testEncode("abc_123", "abc_123")
    }

    @Test
    fun `encode keep period`() {
        testEncode("abc.123", "abc.123")
    }

    @Test
    fun `encode keep tilde`() {
        testEncode("abc~123", "abc~123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.URI_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.URI_ENCODING), "Decode failed")
    }
}
