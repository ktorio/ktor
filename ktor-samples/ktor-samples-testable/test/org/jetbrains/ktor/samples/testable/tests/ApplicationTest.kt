package org.jetbrains.ktor.samples.testable.tests

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.samples.testable.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import kotlin.test.*

class ApplicationTest {
    @Test fun testRequest() = withApplicationFeature<TestableApplication> {
        with (handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("Test String", response.content)
        }
        with (handleRequest(HttpMethod.Get, "/index.html")) {
            assertFalse(requestHandled)
        }
    }
}
