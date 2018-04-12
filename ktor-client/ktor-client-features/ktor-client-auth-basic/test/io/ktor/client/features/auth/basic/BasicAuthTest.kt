package io.ktor.client.features.auth.basic

import org.junit.Test
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
            "Basic VW1sYXV0ZcT89jphJlNlY3JldCUhMjM=",
            BasicAuth.constructBasicAuthValue("UmlauteÄüö", "a&Secret%!23")
        )
    }
}
