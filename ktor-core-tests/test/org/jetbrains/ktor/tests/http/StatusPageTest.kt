package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class StatusPageTest {
    @Test
    fun testStatus404() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Fallback) { call ->
                call.respond(HttpStatusCode.NotFound)
            }

            application.statusPage { status ->
                call.respond(TextContentResponse(status, ContentType.Text.Plain.withCharset(Charsets.UTF_8), "${status.value} ${status.description}"))
            }

            application.routing {
                get("/") {
                    call.respond("ok")
                }
                get("/notFound") {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("ok", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/missing").let { call ->
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/notFound").let { call ->
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
            }
        }
    }

    @Test
    fun testStatus404CustomObject() {
        withTestApplication {
            application.statusPage { status ->
                call.respond(TextContentResponse(status, ContentType.Text.Plain.withCharset(Charsets.UTF_8), "${status.value} ${status.description}"))
            }

            application.intercept(ApplicationCallPipeline.Call) {
                call.respond(object : FinalContent.ChannelContent() {
                    override val status = HttpStatusCode.NotFound

                    override val headers: ValuesMap
                        get() = ValuesMap.Empty

                    override fun channel(): ReadChannel = fail("Should never reach here")
                })
            }

            handleRequest(HttpMethod.Get, "/missing").let { call ->
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
            }
        }
    }

    @Test
    fun testStatus404WithTransform() {
        class O

        withTestApplication {
            application.intercept(ApplicationCallPipeline.Fallback) { call ->
                call.respond(HttpStatusCode.NotFound)
            }

            application.statusPage { status ->
                call.respond(TextContentResponse(status, ContentType.Text.Plain.withCharset(Charsets.UTF_8), "${status.value} ${status.description}"))
            }

            application.transform.register<O> { HttpStatusCode.NotFound }
            application.routing {
                get("/") {
                    call.respond(O())
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
            }
        }
    }

    @Test
    fun testFailPage() {
        withTestApplication {
            application.errorPage { cause ->
                call.respond(TextContentResponse(HttpStatusCode.InternalServerError, ContentType.Text.Plain.withCharset(Charsets.UTF_8), cause.javaClass.simpleName))
            }

            application.routing {
                get("/iae") {
                    throw IllegalArgumentException()
                }
                get("/npe") {
                    throw NullPointerException()
                }
            }

            handleRequest(HttpMethod.Get, "/iae").let { call ->
                assertEquals("IllegalArgumentException", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/npe").let { call ->
                assertEquals("NullPointerException", call.response.content)
            }
        }
    }

    @Test
    fun testFailPageDuringTransform() {
        class O

        withTestApplication {
            application.transform.register<O> { throw IllegalStateException() }
            application.errorPage { cause ->
                call.respond(TextContentResponse(HttpStatusCode.InternalServerError, ContentType.Text.Plain.withCharset(Charsets.UTF_8), cause.javaClass.simpleName))
            }

            application.routing {
                get("/") {
                    call.respond(O())
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("IllegalStateException", call.response.content)
            }
        }
    }

    @Test
    fun testErrorDuringStatus() {
        withTestApplication {
            application.statusPage { throw IllegalStateException("") }
            application.errorPage { cause ->
                call.respond(TextContentResponse(HttpStatusCode.InternalServerError, ContentType.Text.Plain.withCharset(Charsets.UTF_8), cause.javaClass.simpleName))
            }

            application.intercept(ApplicationCallPipeline.Fallback) { call ->
                call.respond(HttpStatusCode.NotFound)
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals("IllegalStateException", call.response.content)
            }
        }
    }

    @Test
    fun testErrorShouldNotRecurse() {
        withTestApplication {
            application.errorPage {
                throw IllegalStateException()
            }

            application.intercept(ApplicationCallPipeline.Fallback) { call ->
                throw NullPointerException()
            }

            assertFails {
                handleRequest(HttpMethod.Get, "/")
            }
        }
    }
}