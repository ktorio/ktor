/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.test.junit.*
import kotlin.test.*

class SerializableTest {
    @Test
    fun urlTest() {
        assertSerializable(Url("https://localhost/path?key=value#fragment"))
    }

    @Test
    fun cookieTest() {
        assertSerializable(Cookie("key", "value"))
    }
}
