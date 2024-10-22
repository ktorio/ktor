// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.junit.*
import kotlin.test.*

class SerializableTest {
    @Test
    fun urlTest() {
        val url = Url("https://localhost/path?key=value#fragment")
        assertEquals(url, assertSerializable(url))
    }

    @Test
    fun cookieTest() {
        val cookie = Cookie("key", "value")
        assertEquals(cookie, assertSerializable(cookie))
    }
}
