package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.*
import org.junit.Test
import org.slf4j.*
import org.slf4j.event.*
import kotlin.test.*

class CallLoggingTest {

    private lateinit var messages: MutableList<String>
    private val logger: Logger = object : Logger by LoggerFactory.getLogger("ktor.test") {
        override fun trace(message: String?) = add("TRACE: $message")
        override fun debug(message: String?) = add("DEBUG: $message")
        override fun info(message: String?) = add("INFO: $message")
        private fun add(message: String?) {
            if (message != null) {
                messages.add(message)
            }
        }
    }
    private val environment = createTestEnvironment {
        module {
            install(CallLogging)
        }
        log = logger
    }


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

        assertEquals("TRACE: Application started: $hash", messages[1])
        assertEquals("TRACE: Application stopping: $hash", messages[2])
        assertEquals("TRACE: Application stopped: $hash", messages[3])
    }

    @Test
    fun `can log an unhandled get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/")
        }

        assertTrue("TRACE: Unhandled: GET - /" in messages)
    }

    @Test
    fun `can log a successful get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/") {
                call.response.status(HttpStatusCode.OK)
            }
        }

        assertTrue("TRACE: 200 OK: GET - /" in messages)
    }

    @Test
    fun `can log a failed get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/") {
                call.response.status(HttpStatusCode.NotFound)
            }
        }

        assertTrue("TRACE: 404 Not Found: GET - /" in messages)
    }

    @Test
    fun `can filter calls to log`() {
        val environment = createTestEnvironment {
            module {
                install(CallLogging) {
                    filter { !it.request.origin.uri.contains("avoid") }
                }
            }
            log = logger
        }

        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/") {
                call.response.status(HttpStatusCode.NotFound)
            }
            handleRequest(HttpMethod.Get, "/avoid") {
                call.response.status(HttpStatusCode.NotFound)
            }
        }

        assertTrue("TRACE: 404 Not Found: GET - /" in messages)
        assertFalse("TRACE: 404 Not Found: GET - /avoid" in messages)
    }

    @Test
    fun `can change log level`() {
        val environment = createTestEnvironment {
            module {
                install(CallLogging) {
                    level = Level.DEBUG
                }
            }
            log = logger
        }

        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/") {
                call.response.status(HttpStatusCode.NotFound)
            }
        }

        assertTrue("DEBUG: 404 Not Found: GET - /" in messages)
    }
}
