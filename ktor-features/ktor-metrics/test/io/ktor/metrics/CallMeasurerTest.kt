package io.ktor.metrics

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class CallMeasurerTest {
    @Test
    fun testNone() {
        withTestApplication {
            application.routing {
                get("/") {
                    call.respondText("HI")
                }
            }
            assertEquals("HI", handleRequest(HttpMethod.Get, "/").response.content)
        }
    }

    @Test
    fun testMeasureWithoutInstalling() {
        withTestApplication {
            application.routing {
                get("/") {
                    measure("hello") {
                        Unit
                    }
                    call.respondText("HI")
                }
            }
            handleRequest(HttpMethod.Get, "/")
            assertEquals("HI", handleRequest(HttpMethod.Get, "/").response.content)
        }
    }

    @Test
    fun testMeasureWithHandler() {
        withTestApplication {
            val log = arrayListOf<String>()
            application.install(CallMeasurer) {
                addHandler {
                    log += it.name
                }
            }
            application.routing {
                get("/") {
                    measure("hello") {
                        Unit
                    }
                    call.respondText("HI")
                }
            }
            assertEquals("HI", handleRequest(HttpMethod.Get, "/").response.content)
            assertEquals(listOf("hello", "total"), log)
        }
    }
}
