package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.*
import org.junit.Test
import org.slf4j.*
import kotlin.test.*

class CallLoggingTest {
    private val environment = createTestEnvironment {
        module {
            install(CallLogging)
        }

        log = object : Logger by LoggerFactory.getLogger("ktor.test") {
            override fun trace(message: String?) = add(message)

            override fun debug(message: String?) = add(message)

            override fun info(message: String?) = add(message)

            private fun add(message: String?) {
                if (message != null) {
                    messages.add(message)
                }
            }
        }
    }

    private lateinit var messages: MutableList<String>

    @Before
    fun setup() {
        messages = ArrayList()
    }

    @Test
    fun `can log application lifecycle events`() {
        var hash: String? = null

        withApplication(environment) {
            hash = application.toString()
        }

        assertEquals("Application started: $hash", messages[1])
        assertEquals("Application stopping: $hash", messages[2])
        assertEquals("Application stopped: $hash", messages[3])
        
//        assertEquals("Application starting: $hash", messages[1])
//        assertEquals("Application started: $hash", messages[2])
//        assertEquals("Application stopping: $hash", messages[3])
//        assertEquals("Application stopped: $hash", messages[4])
    }

    @Test
    fun `can log an unhandled get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/")
        }
        
        assertTrue("Unhandled: GET - /" in messages)
    }

    @Test
    fun `can log a successful get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/") {
                call.response.status(HttpStatusCode.OK)
            }
        }
        
        assertTrue("200 OK: GET - /" in messages)
    }

    @Test
    fun `can log a failed get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/") {
                call.response.status(HttpStatusCode.NotFound)
            }
        }

        assertTrue("404 Not Found: GET - /" in messages)
    }
}
