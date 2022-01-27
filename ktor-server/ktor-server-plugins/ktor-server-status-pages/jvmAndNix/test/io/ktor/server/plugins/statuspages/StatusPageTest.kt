/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.math.*
import kotlin.test.*

class StatusPageTest {
    private val textPlainUtf8 = ContentType.Text.Plain.withCharset(Charsets.UTF_8)

    @Test
    fun testStatus404() {
        withTestApplication {
            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, code ->
                    call.respondText("${code.value} ${code.description}", status = code)
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
                status(HttpStatusCode.NotFound) { call, code ->
                    call.respondText("${code.value} ${code.description}", status = code)
                }
            }

            application.intercept(ApplicationCallPipeline.Call) {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override val status = HttpStatusCode.NotFound
                        override fun readFrom(): ByteReadChannel = fail("Should never reach here")
                    }
                )
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
            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, code ->
                    call.respondText("${code.value} ${code.description}", status = code)
                }
            }

            application.intercept(ApplicationCallPipeline.Plugins) {
                call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                    if (message is O) proceedWith(HttpStatusCode.NotFound)
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
                exception<Throwable> { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.InternalServerError)
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
            application.intercept(ApplicationCallPipeline.Plugins) {
                call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                    if (message is O) {
                        throw IllegalStateException()
                    }
                }
            }

            application.install(StatusPages) {
                exception<IllegalStateException> { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.InternalServerError)
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
                status(HttpStatusCode.NotFound) { _, _ ->
                    throw IllegalStateException("")
                }
                exception<Throwable> { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.InternalServerError)
                }
            }

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
                exception<IllegalStateException> { _, _ ->
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
                exception<ValidationException> { call, cause ->
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
    fun testErrorInAsync(): Unit = withTestApplication {
        class AsyncFailedException : Exception()

        application.install(StatusPages) {
            exception<AsyncFailedException> { call, _ ->
                call.respondText("Async failed")
            }
            exception<CancellationException> { call, _ ->
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

        assertEquals("Async failed", handleRequest(HttpMethod.Get, "/fail").response.content)
        assertEquals("OK", handleRequest(HttpMethod.Get, "/cancel").response.content)
    }

    @Test
    fun testDefaultKtorExceptionWithoutPlugin(): Unit = withTestApplication {
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

        assertEquals(HttpStatusCode.BadRequest, handleRequest(HttpMethod.Get, "/bad-request").response.status())
        val unsupported = handleRequest(HttpMethod.Get, "/media-type-not-supported")
        assertEquals(HttpStatusCode.UnsupportedMediaType, unsupported.response.status())
        assertEquals(HttpStatusCode.NotFound, handleRequest(HttpMethod.Get, "/not-found").response.status())
    }

    @Test
    fun testDefaultKtorExceptionWithPluginHandlingExceptions(): Unit = withTestApplication {
        application.install(StatusPages) {
            exception<BadRequestException> { call, _ ->
                call.respondText("BadRequestException")
            }
            exception<UnsupportedMediaTypeException> { call, _ ->
                call.respondText("UnsupportedMediaTypeException")
            }
            exception<NotFoundException> { call, _ ->
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
    fun testDefaultKtorExceptionWithPluginCustomStatusPages(): Unit = withTestApplication {
        application.install(StatusPages) {
            status(HttpStatusCode.BadRequest) { call, _ ->
                call.respondText("BadRequest")
            }
            status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
                call.respondText("UnsupportedMediaType")
            }
            status(HttpStatusCode.NotFound) { call, _ ->
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

    @Test
    fun testStatusPagesNotCalledAfterRouting() {
        val ThrowingPlugin = createApplicationPlugin("ThrowingPlugin") {
            onCall {
                throw NotFoundException()
            }
        }

        testApplication {
            var exceptionHandled = false
            var routingHandled = false

            application {
                install(ThrowingPlugin)
                install(StatusPages) {
                    exception<NotFoundException> { call: ApplicationCall, cause ->
                        exceptionHandled = true
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                routing {
                    get("/") {
                        routingHandled = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response = client.get("/")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertFalse(routingHandled)
            assertTrue(exceptionHandled)
        }
    }

    @Test
    fun testVerify500OnException() {
        testApplication {
            var exceptionHandled = false
            var routingHandled = false

            application {
                install(StatusPages) {
                    exception<Throwable> { call: ApplicationCall, _ ->
                        exceptionHandled = true
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                routing {
                    get("/") {
                        routingHandled = true
                        throw IllegalArgumentException("something went wrong")
                    }
                }
            }

            val response = client.config {
                expectSuccess = false
            }.get("/")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(routingHandled)
            assertTrue(exceptionHandled)
        }
    }
}
