/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

import io.ktor.client.features.auth.providers.*
import kotlin.test.*

class BasicProviderTest {
    @Test
    fun testUnicodeCredentials() {
        assertEquals(
            "Basic VW1sYXV0ZcOEw7zDtjphJlNlY3JldCUhMjM=",
            buildAuthString("UmlauteÄüö", "a&Secret%!23")
        )
    }

    @Test
    fun testLoginWithColon() {
        assertEquals(
            "Basic dGVzdDo0NzExOmFwYXNzd29yZA==",
            buildAuthString("test:4711", "apassword")
        )
    }

    @Test
    fun testSimpleCredentials() {
        assertEquals(
            "Basic YWRtaW46YWRtaW4=",
            buildAuthString("admin", "admin")
        )
    }

    private fun buildAuthString(username: String, password: String): String =
        constructBasicAuthValue(BasicAuthCredentials(username, password))
}
