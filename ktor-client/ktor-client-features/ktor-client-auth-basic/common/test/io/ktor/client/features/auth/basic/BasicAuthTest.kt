/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.basic

import kotlin.test.*

class BasicAuthTest {

    @Test
    fun testConstructBasicAuthValue() {
        assertEquals(
            "Basic YWRtaW46YWRtaW4=",
            BasicAuth.constructBasicAuthValue("admin", "admin")
        )

        assertEquals(
            "Basic dGVzdDo0NzExOmFwYXNzd29yZA==",
            BasicAuth.constructBasicAuthValue("test:4711", "apassword")
        )

        assertEquals(
            "Basic VW1sYXV0ZcOEw7zDtjphJlNlY3JldCUhMjM=",
            BasicAuth.constructBasicAuthValue("UmlauteÄüö", "a&Secret%!23")
        )
    }
}
