/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class RenderSetCookieTest {

    @Test
    fun renderCookieDoesntEncodeExtensions() {
        val cookie = Cookie(
            "name",
            "value",
            encoding = CookieEncoding.BASE64_ENCODING,
            extensions = mapOf("foo" to "bar")
        )
        val rendered = renderSetCookieHeader(cookie)
        assertEquals("name=dmFsdWU=; foo=bar; \$x-enc=BASE64_ENCODING", rendered)
    }

    @Test
    fun renderCookieExtensionsWithNotRecommendedSymbols() {
        val cookie = Cookie(
            "name",
            "value",
            encoding = CookieEncoding.BASE64_ENCODING,
            extensions = mapOf("foo" to "b,ar")
        )
        val rendered = renderSetCookieHeader(cookie)
        assertEquals("name=dmFsdWU=; foo=b,ar; \$x-enc=BASE64_ENCODING", rendered)
    }

    @Test
    fun renderCookieSetsMaxAge() {
        assertEquals(
            "name=value; Max-Age=0; \$x-enc=URI_ENCODING",
            renderSetCookieHeader("name", "value", maxAge = 0)
        )
        assertEquals(
            "name=value; Max-Age=10; \$x-enc=URI_ENCODING",
            renderSetCookieHeader("name", "value", maxAge = 10)
        )
        assertEquals(
            "name=value; \$x-enc=URI_ENCODING",
            renderSetCookieHeader("name", "value", maxAge = null)
        )
    }
}
