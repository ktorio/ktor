/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class UrlsTest {

    @Test
    fun vagueURLHostParsing() {
        assertEquals("http://localhost", Url("localhost").toString())
        assertEquals("http://127.0.0.1", Url("127.0.0.1").toString())
        assertEquals("http://google.com", Url("google.com").toString())
        assertEquals("http://localhost:8080", Url("localhost:8080").toString())
    }

    @Test
    fun urlBuilder() {
        assertEquals("http://127.0.0.1/", URLBuilder("http://127.0.0.1/").toUrl().toString())
    }

}
