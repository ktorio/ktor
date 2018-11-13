package io.ktor.tests.server.testing

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.coroutines.*
import kotlin.system.*
import kotlin.test.*

class TestApplicationEngineTest {
    @Test
    fun testCustomDispatcher() {
        @UseExperimental(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
        fun CoroutineDispatcher.withDelay(delay: Delay): CoroutineDispatcher =
                object : CoroutineDispatcher(), Delay by delay {
                    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
                            this@withDelay.isDispatchNeeded(context)

                    override fun dispatch(context: CoroutineContext, block: Runnable) =
                            this@withDelay.dispatch(context, block)
                }

        val delayLog = arrayListOf<String>()
        val delayTime = 10_000L

        withTestApplication(
                moduleFunction = {
                    routing {
                        get("/") {
                            delay(delayTime)
                            delay(delayTime)
                            call.respondText("OK")
                        }
                    }
                },
                configure = {
                    @UseExperimental(InternalCoroutinesApi::class)
                    dispatcher = Dispatchers.Unconfined.withDelay(object : Delay {
                        override fun scheduleResumeAfterDelay(
                            timeMillis: Long,
                            continuation: CancellableContinuation<Unit>
                        ) {
                            // Run immediately and log it
                            delayLog += "Delay($timeMillis)"
                            continuation.resume(Unit)
                        }
                    })
                }
        ) {
            val elapsedTime = measureTimeMillis {
                handleRequest(HttpMethod.Get, "/").let { call ->
                    assertTrue(call.requestHandled)
                }
            }
            assertEquals(listOf("Delay($delayTime)", "Delay($delayTime)"), delayLog)
            assertTrue { elapsedTime < (delayTime * 2) }
        }
    }

    @Test
    fun testExceptionHandle() {
        withTestApplication {
            application.install(CallLogging)
            application.routing {
                get("/") {
                    error("Handle me")
                }
            }

            assertFails {
                handleRequest(HttpMethod.Get, "/") {
                }
            }
        }
    }

    @Test
    fun testResponseAwait() {
        withTestApplication {
            application.install(Routing) {
                get("/good") {
                    call.respond(HttpStatusCode.OK, "The Response")
                }
                get("/broken") {
                    delay(100)
                    call.respond(HttpStatusCode.OK, "The Response")
                }
                get("/fail") {
                    error("Handle me")
                }
            }

            with(handleRequest(HttpMethod.Get, "/good")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("The Response", response.content)
            }

            with(handleRequest(HttpMethod.Get, "/broken")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("The Response", response.content)

            }

            assertFailsWith<IllegalStateException> {
                handleRequest(HttpMethod.Get, "/fail")
            }
        }
    }

    @Test
    fun testCookiesSession() {
        data class CountSession(val count: Int)

        withTestApplication {
            application.install(Sessions) {
                cookie<CountSession>("MY_SESSION")
            }
            application.routing {
                get("/") {
                    val session = call.sessions.getOrSet { CountSession(0) }
                    call.sessions.set(session.copy(count = session.count + 1))
                    call.respond(HttpStatusCode.OK, "${session.count}")
                }
            }

            fun doRequestAndCheckResponse(expected: String) {
                handleRequest(HttpMethod.Get, "/").apply { assertEquals(expected, response.content) }
            }

            // By defaul it doesn't preserve cookies
            doRequestAndCheckResponse("0")
            doRequestAndCheckResponse("0")

            // Inside a cookiesSession block cookies are preserved.
            cookiesSession {
                doRequestAndCheckResponse("0")
                doRequestAndCheckResponse("1")
            }

            // Starting another cookiesSession block, doesn't preserve cookies from previous blocks.
            cookiesSession {
                doRequestAndCheckResponse("0")
                doRequestAndCheckResponse("1")
                doRequestAndCheckResponse("2")
            }
        }
    }
}
