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
                route("/world", HttpMethod.Delete) { handle { } }
                route("/world/hello", HttpMethod.Patch) { handle { } }
            }

            checkRoute("/", "")
            checkRoute("/hello/world", "GET, POST")
            checkRoute("/world", "DELETE")
            checkRoute("/world/hello", "PATCH")
        }
    }

    @Test
    fun testWildcards() {
        withOptionsApplication {
            application.routing {
                get("/hello/{names...}") { }
                post("/hello/{name}") { }
                route("/hello/world", HttpMethod.Delete) { handle { } }
                route("/hello/world/nope", HttpMethod.Patch) { handle { } }
            }

            checkRoute("/hello/world", "GET, POST, DELETE")
        }
    }

    @Test
    fun testInnerFilters() {
        withOptionsApplication {
            application.routing {
                route("/demo") {
                    method(HttpMethod.Delete) {
                        route("/test") {
                            handle {  }
                        }
                    }
                    method(HttpMethod.Post) {
                        route("/test") {
                            handle {  }
                        }
                    }
                }
                route("/demo/test", HttpMethod.Patch) {
                    handle {  }
                }
            }

            checkRoute("/demo/test", "DELETE, POST, PATCH")
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