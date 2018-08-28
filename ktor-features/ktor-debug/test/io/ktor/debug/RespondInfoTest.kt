package io.ktor.debug

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RespondInfoTest {
    @Test
    fun testRespondInfo() {
        withTestApplication({
            routing {
                get("/test") {
                    call.respondInfo()
                }
            }
        }) {
            handleRequest(HttpMethod.Get, "/test").apply {
                println(response.content!!)
                assertTrue(response.content!!.contains("/test"))
            }
        }
    }
}