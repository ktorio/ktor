package io.ktor.tests.server.testing

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.system.*
import kotlin.test.*

class TestApplicationEngineTest {
    @Test
    fun testCustomDispatcher() {
        fun CoroutineDispatcher.withDelay(delay: Delay): CoroutineDispatcher =
                object : CoroutineDispatcher(), Delay by delay {
                    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
                            this@withDelay.isDispatchNeeded(context)

                    override fun dispatch(context: CoroutineContext, block: Runnable) =
                            this@withDelay.dispatch(context, block)
                }

        val delayLog = arrayListOf<String>()
        val delayTime = 10_000

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
                    dispatcher = Unconfined.withDelay(object : Delay {
                        override fun scheduleResumeAfterDelay(
                                time: Long,
                                unit: TimeUnit,
                                continuation: CancellableContinuation<Unit>
                        ) {
                            // Run immediately and log it
                            val milliseconds = unit.toMillis(time)
                            delayLog += "Delay($milliseconds)"
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
}
