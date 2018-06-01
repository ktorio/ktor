package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.experimental.io.*
import org.junit.Test
import kotlin.test.*

class HeadTest {

    @Test
    fun testSimple() {
        withHeadApplication {
            application.routing {
                get("/") {
                    call.response.header("M", "1")
                    call.respond("Hello")
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello", call.response.content)
                assertEquals("1", call.response.headers["M"])
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
                assertEquals("1", call.response.headers["M"])
            }
        }
    }

    @Test
    fun testTextContent() {
        withHeadApplication {
            application.routing {
                get("/") {
                    call.respondText("Hello")
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello", call.response.content)
                assertEquals("text/plain; charset=UTF-8", call.response.headers[HttpHeaders.ContentType])
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
                assertEquals("text/plain; charset=UTF-8", call.response.headers[HttpHeaders.ContentType])
            }
        }
    }

    @Test
    fun testTextRespond() {
        withHeadApplication {
            application.routing {
                get("/") {
                    call.respondWrite {
                        write("Hello")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello", call.response.content)
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testCustomOutgoingContent() {
        withHeadApplication {
            application.routing {
                get("/") {
                    call.respond(object : OutgoingContent.ReadChannelContent() {
                        override fun readFrom() = ByteReadChannel("Hello".toByteArray())

                        override val headers: Headers
                            get() = Headers.build {
                                append("M", "2")
                            }
                    })
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNotNull(call.response.byteContent)
                assertEquals("Hello", call.response.content)
                assertEquals("2", call.response.headers["M"])
                assertEquals("2", call.response.headers["m"])
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
                assertEquals("2", call.response.headers["M"])
                assertEquals("2", call.response.headers["m"])
            }
        }
    }

    private fun withHeadApplication(block: TestApplicationEngine.() -> Unit) {
        withTestApplication {
            application.install(AutoHeadResponse)

            block()
        }
    }
}