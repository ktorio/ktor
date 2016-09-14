package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class HeadTest {

    @Test
    fun testSimple() {
        withHeadApplication {
            application.routing {
                route("/") {
                    handle {
                        call.response.header("M", "1")
                        call.respond("Hello")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNotNull(call.response.byteContent)
                assertEquals("Hello", call.response.content)
                assertEquals("1", call.response.headers["M"])
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.byteContent)
                assertNull(call.response.content)
                assertEquals("1", call.response.headers["M"])
            }
        }
    }

    @Test
    fun testTextContent() {
        withHeadApplication {
            application.routing {
                route("/") {
                    handle {
                        call.respond(TextContent(ContentType.Text.Plain, "Hello"))
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNotNull(call.response.byteContent)
                assertEquals("Hello", call.response.content)
                assertEquals("text/plain", call.response.headers[HttpHeaders.ContentType])
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.byteContent)
                assertNull(call.response.content)
                assertEquals("text/plain", call.response.headers[HttpHeaders.ContentType])
            }
        }
    }

    @Test
    fun testTextRespond() {
        withHeadApplication {
            application.routing {
                route("/") {
                    handle {
                        call.respondWrite {
                            write("Hello")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNotNull(call.response.byteContent)
                assertEquals("Hello", call.response.content)
            }

            handleRequest(HttpMethod.Head, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.byteContent)
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testCustomFinalContent() {
        withHeadApplication {
            application.routing {
                route("/") {
                    handle {
                        call.respond(object : FinalContent.ChannelContent() {
                            override fun channel() = ByteArrayReadChannel("Hello".toByteArray())

                            override val headers: ValuesMap
                                get() = ValuesMap.build(true) {
                                    append("M", "2")
                                }
                        })
                    }
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
                assertNull(call.response.byteContent)
                assertNull(call.response.content)
                assertEquals("2", call.response.headers["M"])
            }
        }
    }

    private fun withHeadApplication(block: TestApplicationHost.() -> Unit) {
        withTestApplication {
            application.install(HeadRequestSupport)

            block()
        }
    }
}