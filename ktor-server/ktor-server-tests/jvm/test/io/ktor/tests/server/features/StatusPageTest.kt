/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import io.ktor.utils.io.*
import kotlin.test.*

class StatusPageTest {
    private val textPlainUtf8 = ContentType.Text.Plain.withCharset(Charsets.UTF_8)
    private val textHtmlUtf8 = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    @Test
    fun testStatusMapping() {
        withTestApplication {
            application.install(StatusPages) {
                statusFile(HttpStatusCode.NotFound, filePattern = "error#.html")
            }
            application.intercept(ApplicationCallPipeline.Call) {
                call.respond(HttpStatusCode.NotFound)
            }
            handleRequest(HttpMethod.Get, "/foo").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status(), "Actual status must be kept")
                assertEquals("<html><body>error 404</body></html>", call.response.content)
                assertEquals(textHtmlUtf8, call.response.contentType())
            }
        }
    }

    @Test
    fun testStatusMappingWithRoutes() {
        withTestApplication {
            application.routing {
                route("/foo") {
                    route("/wee") {
                        handle {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                    install(StatusPages) {
                        statusFile(HttpStatusCode.NotFound, filePattern = "error#.html")
                    }
                    route("{...}") {
                        handle {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
            handleRequest(HttpMethod.Get, "/foo").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
                assertEquals("<html><body>error 404</body></html>", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/foo/wee").let { call ->
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals(null, call.response.content)
            }
        }
    }

    @Test
    fun testStatus404() {
        withTestApplication {
            installFallback()

            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) {
                    call.respondText("${it.value} ${it.description}", status = it)
                }
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
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("ok", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/missing").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
                assertEquals(textPlainUtf8, call.response.contentType())
            }

            handleRequest(HttpMethod.Get, "/notFound").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
                assertEquals(textPlainUtf8, call.response.contentType())
            }
        }
    }

    @Test
    fun testStatus404CustomObject() {
        withTestApplication {
            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) {
                    call.respondText("${it.value} ${it.description}", status = it)
                }
            }

            application.intercept(ApplicationCallPipeline.Call) {
                call.respond(object : OutgoingContent.ReadChannelContent() {
                    override val status = HttpStatusCode.NotFound
                    override fun readFrom(): ByteReadChannel = fail("Should never reach here")
                })
            }

            handleRequest(HttpMethod.Get, "/missing").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
                assertEquals(textPlainUtf8, call.response.contentType())
            }
        }
    }

    @Test
    fun testStatus404WithInterceptor() {
        class O

        withTestApplication {
            installFallback()

            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) {
                    call.respondText("${it.value} ${it.description}", status = it)
                }
            }

            application.intercept(ApplicationCallPipeline.Features) {
                call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                    if (message is O)
                        proceedWith(HttpStatusCode.NotFound)
                }
            }

            application.routing {
                get("/") {
                    call.respond(O())
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
                assertEquals("404 ${HttpStatusCode.NotFound.description}", call.response.content)
            }
        }
    }

    @Test
    fun testFailPage() {
        withTestApplication {
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respondText(cause::class.java.simpleName, status = HttpStatusCode.InternalServerError)
                }
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
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals("IllegalArgumentException", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/npe").let { call ->
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals("NullPointerException", call.response.content)
            }
        }
    }

    @Test
    fun testFailPageDuringInterceptor() {
        class O

        withTestApplication {
            application.intercept(ApplicationCallPipeline.Features) {
                call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                    if (message is O)
                        throw IllegalStateException()
                }
            }

            application.install(StatusPages) {
                exception<IllegalStateException> { cause ->
                    call.respondText(cause::class.java.simpleName, status = HttpStatusCode.InternalServerError)
                }
            }

            application.routing {
                get("/") {
                    call.respond(O())
                }
            }

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals("IllegalStateException", call.response.content)
            }
        }
    }

    @Test
    fun testErrorDuringStatus() {
        withTestApplication {
            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) {
                    throw IllegalStateException("")
                }
                exception<Throwable> { cause ->
                    call.respondText(cause::class.java.simpleName, status = HttpStatusCode.InternalServerError)
                }
            }

            installFallback()

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals("IllegalStateException", call.response.content)
            }
        }
    }

    @Test
    fun testErrorShouldNotRecurse() {
        withTestApplication {
            application.install(StatusPages) {
                exception<IllegalStateException> {
                    throw IllegalStateException()
                }
            }

            application.intercept(ApplicationCallPipeline.Fallback) {
                throw NullPointerException()
            }

            assertFails {
                handleRequest(HttpMethod.Get, "/")
            }
        }
    }

    @Test
    fun testErrorFromExceptionContent() {
        class ValidationException(val code: String) : RuntimeException()

        withTestApplication {
            application.install(StatusPages) {
                exception<ValidationException> { cause ->
                    // Can access `cause.code` without casting
                    call.respondText(cause.code, status = HttpStatusCode.InternalServerError)
                }
            }

            application.routing {
                get("/ve") {
                    throw ValidationException("code")
                }
            }

            handleRequest(HttpMethod.Get, "/ve").let { call ->
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals("code", call.response.content)
            }
        }
    }

    @Test
    @Suppress("RedundantAsync", "IMPLICIT_NOTHING_AS_TYPE_PARAMETER", "ReplaceSingleLineLet")
    fun testErrorInAsync(): Unit = withTestApplication<Unit> {
        class AsyncFailedException : Exception()

        application.install(StatusPages) {
            exception<AsyncFailedException> {
                call.respondText("Async failed")
            }
            exception<CancellationException> {
                call.respondText("Cancelled")
            }
        }

        application.routing {
            get("/fail") {
                async { throw AsyncFailedException() }.await()
            }
            get("/cancel") {
                val j = launch {
                    delay(1000000L)
                }
                j.cancel()
                call.respondText("OK")
            }
        }

        handleRequest(HttpMethod.Get, "/fail").let {
            assertEquals("Async failed", it.response.content)
        }

        handleRequest(HttpMethod.Get, "/cancel").let {
            assertEquals("OK", it.response.content)
        }
    }

    @Suppress("ReplaceSingleLineLet")
    @Test
    fun testDefaultKtorExceptionWithoutFeature(): Unit = withTestApplication {
        application.routing {
            get("/bad-request") {
                throw BadRequestException("Planned failure")
            }
            get("/media-type-not-supported") {
                throw UnsupportedMediaTypeException(ContentType.Text.Plain)
            }
            get("/not-found") {
                throw NotFoundException()
            }
        }

        handleRequest(HttpMethod.Get, "/bad-request").let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
        handleRequest(HttpMethod.Get, "/media-type-not-supported").let { call ->
            assertEquals(HttpStatusCode.UnsupportedMediaType, call.response.status())
        }
        handleRequest(HttpMethod.Get, "/not-found").let { call ->
            assertEquals(HttpStatusCode.NotFound, call.response.status())
        }
    }

    @Test
    fun testDefaultKtorExceptionWithFeatureHandlingExceptions(): Unit = withTestApplication {
        application.install(StatusPages) {
            exception<BadRequestException> {
                call.respondText("BadRequestException")
            }
            exception<UnsupportedMediaTypeException> {
                call.respondText("UnsupportedMediaTypeException")
            }
            exception<NotFoundException> {
                call.respondText("NotFoundException")
            }
        }

        application.routing {
            get("/bad-request") {
                throw BadRequestException("Planned failure")
            }
            get("/media-type-not-supported") {
                throw UnsupportedMediaTypeException(ContentType.Text.Plain)
            }
            get("/not-found") {
                throw NotFoundException()
            }
        }

        handleRequest(HttpMethod.Get, "/bad-request").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("BadRequestException", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/media-type-not-supported").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("UnsupportedMediaTypeException", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/not-found").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("NotFoundException", call.response.content)
        }
    }

    @Test
    fun testDefaultKtorExceptionWithFeatureCustomStatusPages(): Unit = withTestApplication {
        application.install(StatusPages) {
            status(HttpStatusCode.BadRequest) {
                call.respondText("BadRequest")
            }
            status(HttpStatusCode.UnsupportedMediaType) {
                call.respondText("UnsupportedMediaType")
            }
            status(HttpStatusCode.NotFound) {
                call.respondText("NotFound")
            }
        }

        application.routing {
            get("/bad-request") {
                throw BadRequestException("Planned failure")
            }
            get("/media-type-not-supported") {
                throw UnsupportedMediaTypeException(ContentType.Text.Plain)
            }
            get("/not-found") {
                throw NotFoundException()
            }
        }

        handleRequest(HttpMethod.Get, "/bad-request").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("BadRequest", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/media-type-not-supported").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("UnsupportedMediaType", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/not-found").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("NotFound", call.response.content)
        }
    }
}

private fun TestApplicationEngine.installFallback() {
    application.intercept(ApplicationCallPipeline.Fallback) {
        if (call.response.status() == null) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
