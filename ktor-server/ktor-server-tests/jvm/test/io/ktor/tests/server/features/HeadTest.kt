/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.lang.IllegalStateException
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
                    call.respondTextWriter {
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
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override fun readFrom() = ByteReadChannel("Hello".toByteArray())

                            override val headers: Headers
                                get() = Headers.build {
                                    append("M", "2")
                                }
                        }
                    )
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

    @Test
    fun testWithStatusPages() = withHeadApplication {
        application.install(StatusPages) {
            exception<IllegalStateException> {
                call.respondText("ISE: ${it.message}", status = HttpStatusCode.InternalServerError)
            }
            status(HttpStatusCode.NotFound) { call.respondText("Not found", status = HttpStatusCode.NotFound) }
        }

        application.routing {
            get("/page1") {
                call.respondText("page1 OK")
            }
            get("/page2") {
                throw IllegalStateException("page2 failed")
            }
        }

        // ensure with GET
        handleRequest(HttpMethod.Get, "/page1").let { call ->
            assertEquals("page1 OK", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/page2").let { call ->
            assertEquals(500, call.response.status()?.value)
            assertEquals("ISE: page2 failed", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/page3").let { call ->
            assertEquals(404, call.response.status()?.value)
            assertEquals("Not found", call.response.content)
        }

        // test HEAD itself
        handleRequest(HttpMethod.Head, "/page1").let { call ->
            assertEquals("page1 OK".length.toString(), call.response.headers[HttpHeaders.ContentLength])
            assertNull(call.response.content)
        }

        handleRequest(HttpMethod.Head, "/page2").let { call ->
            assertEquals(500, call.response.status()?.value)
            assertEquals("ISE: page2 failed".length.toString(), call.response.headers[HttpHeaders.ContentLength])
            assertNull(call.response.content)
        }

        handleRequest(HttpMethod.Head, "/page3").let { call ->
            assertEquals(404, call.response.status()?.value)
            assertEquals("Not found".length.toString(), call.response.headers[HttpHeaders.ContentLength])
            assertNull(call.response.content)
        }
    }

    private fun withHeadApplication(block: TestApplicationEngine.() -> Unit) {
        withTestApplication {
            application.install(AutoHeadResponse)

            block()
        }
    }
}
