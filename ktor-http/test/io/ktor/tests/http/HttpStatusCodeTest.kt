package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Test
import kotlin.test.*

class HttpStatusCodeTest {
    @Test
    fun HttpStatusCodeAll() {
        assertEquals(47, HttpStatusCode.allStatusCodes.size)
    }

    @Test
    fun HttpStatusCodeFromValue() {
        assertEquals(HttpStatusCode.NotFound, HttpStatusCode.fromValue(404))
    }

    @Test
    fun HttpStatusCodeConstructed() {
        assertEquals(HttpStatusCode.NotFound, HttpStatusCode(404, "Not Found"))
    }

    @Test
    fun HttpStatusCodeWithDescription() {
        assertNotEquals(HttpStatusCode.NotFound, HttpStatusCode.NotFound.description("Missing Resource"))
    }

    @Test
    fun HttpStatusCodeToString() {
        assertEquals("404 Not Found", HttpStatusCode.NotFound.toString())
    }
}