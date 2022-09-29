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
    fun renderCookieDoesntThrowsOnNotEncodedExtensions() {
        val cookie = Cookie(
            "name",
            "value",
            encoding = CookieEncoding.BASE64_ENCODING,
            extensions = mapOf("foo" to "b,ar")
        )
        assertFailsWith<IllegalArgumentException> {
            renderSetCookieHeader(cookie)
        }
    }
}
