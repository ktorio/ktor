package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Test
import org.slf4j.*
import org.slf4j.event.*
import kotlin.test.*

@UseExperimental(ObsoleteCoroutinesApi::class)
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
                                .joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}"}
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
            newSingleThreadContext("mdc-test-ctx").use { dispatcher ->
                application.routing {
                    get("/*") {
                        withContext(dispatcher) {
                            application.log.info("test message")
                        }
                        call.respond("OK")
                    }
                }

                handleRequest(HttpMethod.Get, "/uri1").let { call ->
                    assertTrue { call.requestHandled }

                    println(messages.joinToString("\n"))
                    assertTrue { "INFO: test message [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
                    assertTrue { "TRACE: 200 OK: GET - /uri1 [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
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
                    assertTrue { call.requestHandled }

                    println(messages.joinToString("\n"))
                    assertTrue { "INFO: test message [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
                    assertTrue { "TRACE: 200 OK: GET - /uri1 [mdc-call-id=generated-call-id-0, mdc-uri=/uri1]" in messages }
                }
            }
        }
    }

}
