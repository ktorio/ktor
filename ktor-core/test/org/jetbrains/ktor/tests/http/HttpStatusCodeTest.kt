package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.junit.Test
import kotlin.test.assertEquals

class HttpStatusCodeTest {
    @Test
    fun testAllStatusCodes() {
        assertEquals(HttpStatusCode.allStatusCodes.size, 47)
    }
}