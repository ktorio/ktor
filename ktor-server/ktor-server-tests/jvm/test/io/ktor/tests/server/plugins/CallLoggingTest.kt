/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.fusesource.jansi.*
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
    private val environment: ApplicationEngineEnvironmentBuilder.() -> Unit = {
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

        testApplication {
            environment { environment() }
            application {
                hash = this.toString()
            }
        }

        assertTrue(messages.size >= 3, "It should be at least 3 message logged:\n$messages")
        assertEquals(
            "INFO: Application started: $hash",
            messages[messages.size - 3],
            "No started message logged:\n$messages"
        )
        assertEquals(
            "INFO: Application stopping: $hash",
            messages[messages.size - 2],
            "No stopping message logged:\n$messages"
        )
        assertEquals(
            "INFO: Application stopped: $hash",
            messages[messages.size - 1],
            "No stopped message logged:\n$messages"
        )
    }

    @Test
    fun `can log an unhandled get request`() = testApplication {
        environment { environment() }

        createClient { expectSuccess = false }.get("/")

        assertTrue("INFO: ${red("404 Not Found")}: ${cyan("GET")} - /" in messages)
    }

    @Test
    fun `can log a successful get request`() = testApplication {
        environment { environment() }
        routing {
            get {
                call.respond(HttpStatusCode.OK)
            }
        }

        client.get("/")

        assertTrue("INFO: ${green("200 OK")}: ${cyan("GET")} - /" in messages)
    }

    @Test
    fun `can customize message format`() = testApplication {
        environment {
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

        client.get("/uri-123")
        assertTrue("INFO: /uri-123 -> 200 OK" in messages)
    }

    @Test
    fun `can filter calls to log`() = testApplication {
        environment {
            module {
                install(CallLogging) {
                    filter { !it.request.origin.uri.contains("avoid") }
                }
            }
            log = logger
        }
        val client = createClient { expectSuccess = false }
        client.get("/")
        client.get("/avoid")

        assertTrue("INFO: ${red("404 Not Found")}: ${cyan("GET")} - /" in messages)
        assertFalse("INFO: ${red("404 Not Found")}: ${cyan("GET")} - /avoid" in messages)
    }

    @Test
    fun `can change log level`() = testApplication {
        environment {
            module {
                install(CallLogging) {
                    level = Level.DEBUG
                }
            }
            log = logger
        }
        routing {
            get {
                call.respond(HttpStatusCode.OK)
            }
        }

        client.get("/")

        assertTrue("DEBUG: ${green("200 OK")}: ${cyan("GET")} - /" in messages)
    }

    @Test
    fun `can fill MDC after call`() = testApplication {
        environment {
            module {
                install(CallLogging) {
                    mdc("mdc-uri") { it.request.uri }
                    mdc("mdc-status") { it.response.status()?.value?.toString() }
                    format { it.request.uri }
                }
            }
            log = logger
        }
        routing {
            get("/*") {
                application.log.info("test message")
                call.respond("OK")
            }
        }

        client.get("/uri1")
        assertTrue { "INFO: test message [mdc-uri=/uri1]" in messages }
        assertTrue { "INFO: /uri1 [mdc-status=200, mdc-uri=/uri1]" in messages }
    }

    @Test
    fun `can fill MDC before routing`() = testApplication {
        val TestPlugin = createApplicationPlugin("TestPlugin") {
            onCall { it.response.headers.append("test-header", "test-value") }
        }
        environment {
            module {
                install(CallLogging) {
                    mdc("mdc-test-header") { it.response.headers["test-header"] }
                    mdc("mdc-status") { it.response.status()?.value?.toString() }
                    format { it.request.uri }
                }
                install(TestPlugin)
            }
            log = logger
        }
        routing {
            get("/*") {
                application.log.info("test message")
                call.respond("OK")
            }
        }

        client.get("/uri1")
        assertTrue("INFO: test message [mdc-test-header=test-value]" in messages)
        assertTrue("INFO: /uri1 [mdc-status=200, mdc-test-header=test-value]" in messages)
    }

    @Test
    fun `can fill MDC and survive context switch`() = testApplication {
        var counter = 0
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        environment {
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
        application {
            routing {
                get("/*") {
                    withContext(dispatcher) {
                        application.log.info("test message")
                    }
                    call.respond("OK")
                }
            }
        }

        client.get("/uri1")
        assertTrue { "INFO: test message [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
        assertTrue {
            "INFO: ${green("200 OK")}: ${cyan("GET")} - " +
                "/uri1 [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages
        }
        dispatcher.close()
    }

    @Test
    fun `can fill MDC and survive context switch in IOCoroutineDispatcher`() = testApplication {
        var counter = 0

        @OptIn(ObsoleteCoroutinesApi::class)
        val dispatcher = newFixedThreadPoolContext(1, "test-dispatcher")
        environment {
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
        application {
            routing {
                get("/*") {
                    withContext(dispatcher) {
                        application.log.info("test message")
                    }
                    call.respond("OK")
                }
            }
        }

        client.get("/uri1")
        assertTrue { "INFO: test message [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
        assertTrue {
            "INFO: ${green("200 OK")}: ${cyan("GET")} - " +
                "/uri1 [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages
        }
        dispatcher.close()
    }

    @Test
    fun `can configure custom logger`() {
        val customMessages = ArrayList<String>()
        val customLogger: Logger = object : Logger by LoggerFactory.getLogger("ktor.test.custom") {
            override fun info(message: String?) {
                if (message != null) {
                    customMessages.add("CUSTOM TRACE: $message")
                }
            }
        }
        var hash: String? = null

        testApplication {
            environment {
                module {
                    install(CallLogging) {
                        this.logger = customLogger
                    }
                }
            }
            application { hash = this.toString() }
        }

        assertTrue(customMessages.isNotEmpty())
        assertTrue(customMessages.all { it.startsWith("CUSTOM TRACE:") && it.contains(hash!!) })
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `can log without colors`() = testApplication {
        environment {
            module {
                install(CallLogging) {
                    disableDefaultColors()
                }
            }
            log = logger
        }
        createClient { expectSuccess = false }.get("/")

        assertTrue("INFO: 404 Not Found: GET - /" in messages)
    }

    private fun green(value: Any): String = colored(value, Ansi.Color.GREEN)
    private fun red(value: Any): String = colored(value, Ansi.Color.RED)
    private fun cyan(value: Any): String = colored(value, Ansi.Color.CYAN)

    private fun colored(value: Any, color: Ansi.Color): String =
        Ansi.ansi().fg(color).a(value).reset().toString()
}
