package io.ktor.tests.server.http

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.test.*

class RespondWriteTest {
    @Test
    fun smoke() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondWrite { write("OK") }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testFailureInside() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondWrite {
                        throw IllegalStateException("expected")
                    }
                }
            }

            assertFailsWith<IllegalStateException> {
                handleRequest(HttpMethod.Get, "/").awaitCompletion()
            }
        }
    }

    @Test
    fun testSuspendInside() {
        withTestApplication {
            val executor = Executors.newSingleThreadExecutor()
            environment.monitor.subscribe(ApplicationStopped) { executor.shutdown() }
            application.routing {
                get("/") {
                    call.respondWrite {
                        run(executor.asCoroutineDispatcher()) {
                            write("OK")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("OK", call.response.content)
            }
        }
    }

    //    @Test
    @Suppress("UNUSED")
    fun testFailureInsideUnresolvedCase() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondWrite {
                        close() // after that point the main pipeline is going to continue since the channel is closed
                        // so we can't simply bypass an exception
                        // the workaround is to hold pipeline on channel pipe until commit rather than just close

                        Thread.sleep(1000)
                        throw IllegalStateException("expected")
                    }
                }
            }

            assertFailsWith<IllegalStateException> {
                handleRequest(HttpMethod.Get, "/")
            }
        }
    }
}