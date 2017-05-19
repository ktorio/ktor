package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.junit.*
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
                handleRequest(HttpMethod.Get, "/")
            }
        }
    }

    @Test
    fun testSuspendInside() {
        withTestApplication {
            val executor = Executors.newSingleThreadExecutor()
            environment.monitor.applicationStopped += { executor.shutdown() }
            application.routing {
                get("/") {
                    call.respondWrite {
                        runAsync(executor) {
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