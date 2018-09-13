package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class OptionsTest {
    @Test
    fun testSimple() {
        withOptionsApplication {
            application.routing {
                get("/") { }
                post("/") { }
                route("/hello") {
                    route("/world", HttpMethod.Delete) { handle { } }
                    route("/world", HttpMethod.Patch) { handle { } }
                }
            }

            checkRoute("/", "GET, POST")
            checkRoute("/hello/world", "DELETE, PATCH")
            checkRoute("/demo", "")
        }
    }

    @Test
    fun testParameter() {
        withOptionsApplication {
            application.routing {
                get("/hello/world") { }
                post("/hello/{name}") { }
            }

            checkRoute("/", "")
            checkRoute("/hello/world", "GET, POST")
        }
    }

    private fun TestApplicationEngine.checkRoute(path: String, allow: String) {
        handleRequest(HttpMethod.Options, path).let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals(allow, call.response.headers["Allow"])
        }
    }

    private fun withOptionsApplication(block: TestApplicationEngine.() -> Unit) {
        withTestApplication {
            application.install(AutoOptionsResponse)

            block()
        }
    }
}