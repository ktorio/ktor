package io.ktor.samples.testable.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.samples.testable.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class ApplicationTest {
    @Test fun testRequest() = withTestApplication(Application::testableApplication) {
        with(handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("Test String", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/index.html")) {
            assertFalse(requestHandled)
        }
    }
}
