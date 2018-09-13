package io.ktor.metrics

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class ServerTimingTest {
    @Test
    fun testServerTiming() {
        withTestApplication {
            application.install(ServerTiming)
            application.routing {
                get("/") {
                    measure("hello") { Unit }
                    measure("world", "This is a Hello World!") { Unit }
                    call.respondText("HI")
                }
            }
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals("HI", response.content)
                assertEquals(
                    listOf(
                        "hello;dur=*",
                        "world;desc=\"This is a Hello World!\";dur=*",
                        "total;dur=*"
                    ),
                    response.headers.values("Server-Timing").map { it.replace(Regex("dur=\\d+(\\.\\d+)?"), "dur=*") }
                )
            }
        }
    }
}