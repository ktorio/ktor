package org.jetbrains.ktor.samples.testable.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.samples.testable.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import kotlin.test.*

public class ApplicationTest {
    Test fun testRequest() = withApplication<TestableApplication> {
        with (handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK.value, response.status())
            assertEquals("Test String", response.content)
        }
        with (handleRequest(HttpMethod.Get, "/index.html")) {
            assertEquals(requestResult, ApplicationRequestStatus.Unhandled)
        }
    }
}
