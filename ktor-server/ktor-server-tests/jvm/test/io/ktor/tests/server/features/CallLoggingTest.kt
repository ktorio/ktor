/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.slf4j.*
import org.slf4j.event.*
import java.util.concurrent.*
import kotlin.test.*

class CallLoggingTest {

    private lateinit var messages: MutableList<String>
    private val logger: Logger = object : Logger by LoggerFactory.getLogger("ktor.test") {
        override fun trace(message: String?) = add("TRACE: $message")
        override fun debug(message: String?) = add("DEBUG: $message")
        override fun info(message: String?) = add("INFO: $message")

        private fun add(message: String?) {
            if (message != null) {
                val mdcText = MDC.getCopyOfContextMap()?.let { mdc ->
                    if (mdc.isNotEmpty()) {
                        mdc.entries.sortedBy { it.key }
                            .joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
                    } else {
                        ""
                    }
                } ?: ""

                messages.add(message + mdcText)
            }
        }
    }
    private val environment = createTestEnvironment {
        module {
            install(CallLogging)
        }
        log = logger
    }

    @BeforeTest
    fun setup() {
        messages = ArrayList()
    }

    @Test
    fun `can log application lifecycle events`() {
        var hash: String? = null

        withApplication(environment) {
            hash = application.toString()
        }

        assertTrue(messages.size >= 3, "It should be at least 3 message logged:\n$messages")
        assertEquals(
            "TRACE: Application started: $hash",
            messages[messages.size - 3],
            "No started message logged:\n$messages"
        )
        assertEquals(
            "TRACE: Application stopping: $hash",
            messages[messages.size - 2],
            "No stopping message logged:\n$messages"
        )
        assertEquals(
            "TRACE: Application stopped: $hash",
            messages[messages.size - 1],
            "No stopped message logged:\n$messages"
        )
    }

    @Test
    fun `can log an unhandled get request`() {
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/")
        }

        assertTrue("TRACE: 404 Not Found: GET - /" in messages)
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
    fun `can customize message format`() {
        val environment = createTestEnvironment {
            module {
                install(CallLogging) {
                    format { call ->
                        "${call.request.uri} -> ${call.response.status()}"
                    }
                }
                routing {
                    get("/{...}") {
                        call.respondText("OK")
                    }
                }
            }
            log = logger
        }
        withApplication(environment) {
            handleRequest(HttpMethod.Get, "/uri-123").let { call ->
                assertEquals("OK", call.response.content)

                assertTrue("TRACE: /uri-123 -> 200 OK" in messages)
            }
        }
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

    @Test
    fun `can fill MDC and survive context switch`() {
        var counter = 0

        val environment = createTestEnvironment {
            module {
                install(CallLogging) {
                    mdc("mdc-uri") { it.request.uri }
                    callIdMdc("mdc-call-id")
                }
                install(CallId) {
                    generate { "generated-call-id-${counter++}" }
                }
            }
            log = logger
        }

        withApplication(environment) {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                application.routing {
                    get("/*") {
                        withContext(dispatcher) {
                            application.log.info("test message")
                        }
                        call.respond("OK")
                    }
                }

                handleRequest(HttpMethod.Get, "/uri1").let { call ->
                    assertTrue { "INFO: test message [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
                    assertTrue {
                        "TRACE: 200 OK: GET - /uri1 [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages
                    }
                }
            }
        }
    }

    @Test
    fun `can fill MDC and survive context switch in IOCoroutineDispatcher`() {
        var counter = 0

        val environment = createTestEnvironment {
            module {
                install(CallLogging) {
                    mdc("mdc-uri") { it.request.uri }
                    callIdMdc("mdc-call-id")
                }
                install(CallId) {
                    generate { "generated-call-id-${counter++}" }
                }
            }
            log = logger
        }

        withApplication(environment) {
            @OptIn(ObsoleteCoroutinesApi::class)
            newFixedThreadPoolContext(1, "test-dispatcher").use { dispatcher ->
                application.routing {
                    get("/*") {
                        withContext(dispatcher) {
                            application.log.info("test message")
                        }
                        call.respond("OK")
                    }
                }

                handleRequest(HttpMethod.Get, "/uri1").let { call ->
                    assertTrue { "INFO: test message [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
                    assertTrue {
                        "TRACE: 200 OK: GET - /uri1 [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages
                    }
                }
            }
        }
    }

    @Test
    fun `can configure custom logger`() {
        val customMessages = ArrayList<String>()
        val customLogger: Logger = object : Logger by LoggerFactory.getLogger("ktor.test.custom") {
            override fun trace(message: String?) {
                if (message != null) {
                    customMessages.add("CUSTOM TRACE: $message")
                }
            }
        }
        val environment = createTestEnvironment {
            module {
                install(CallLogging) {
                    this.logger = customLogger
                }
            }
        }
        var hash: String? = null

        withApplication(environment) {
            hash = application.toString()
        }

        assertTrue(customMessages.isNotEmpty())
        assertTrue(customMessages.all { it.startsWith("CUSTOM TRACE:") && it.contains(hash!!) })
        assertTrue(messages.isEmpty())
    }
}
