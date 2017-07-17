package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
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
                    call.respond(TextContent("Hello", ContentType.Text.Plain))
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello", call.response.content)
                assertEquals("text/plain", call.response.headers[HttpHeaders.ContentType])
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
                assertEquals("text/plain", call.response.headers[HttpHeaders.ContentType])
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
    fun testCustomFinalContent() {
        withHeadApplication {
            application.routing {
                get("/") {
                    call.respond(object : FinalContent.ReadChannelContent() {
                        override fun readFrom() = "Hello".toByteArray().toReadChannel()

                        override val headers: ValuesMap
                            get() = ValuesMap.build(true) {
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
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.content)
                assertEquals("2", call.response.headers["M"])
            }
        }
    }

    private fun withHeadApplication(block: TestApplicationHost.() -> Unit) {
        withTestApplication {
            application.install(AutoHeadResponse)

            block()
        }
    }
}