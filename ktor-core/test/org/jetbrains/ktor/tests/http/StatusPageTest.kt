package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.transform.*
import org.junit.*
import kotlin.test.*

class StatusPageTest {
    @Test
    fun testStatus404() {
        withTestApplication {
            pipeline.intercept(ApplicationCallPipeline.Fallback) { call ->
                call.respond(HttpStatusCode.NotFound)
            }

            application.statusPage { status ->
                call.respond(TextContentResponse(status, ContentType.Text.Plain.withParameter("charset", "UTF-8"), "${status.value} ${status.description}"))
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
    fun testStatus404WithTransform() {
        class O

        withTestApplication {
            pipeline.intercept(ApplicationCallPipeline.Fallback) { call ->
                call.respond(HttpStatusCode.NotFound)
            }

            application.statusPage { status ->
                call.respond(TextContentResponse(status, ContentType.Text.Plain.withParameter("charset", "UTF-8"), "${status.value} ${status.description}"))
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
                call.respond(TextContentResponse(HttpStatusCode.InternalServerError, ContentType.Text.Plain.withParameter("charset", "UTF-8"), "${cause.javaClass.simpleName}"))
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
                call.respond(TextContentResponse(HttpStatusCode.InternalServerError, ContentType.Text.Plain.withParameter("charset", "UTF-8"), "${cause.javaClass.simpleName}"))
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
}