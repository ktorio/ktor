/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import kotlin.test.*

class StatusPagesTest {
    private val textPlainUtf8 = ContentType.Text.Plain.withCharset(Charsets.UTF_8)

    @Test
    fun testStatus404() = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, code ->
                    call.respondText("${code.value} ${code.description}", status = code)
                }
            }

            routing {
                get("/") {
                    call.respond("ok")
                }
                get("/notFound") {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        client.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }

        client.get("/missing").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("404 ${HttpStatusCode.NotFound.description}", response.bodyAsText())
            assertEquals(textPlainUtf8, response.contentType())
        }

        client.get("/notFound").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("404 ${HttpStatusCode.NotFound.description}", response.bodyAsText())
            assertEquals(textPlainUtf8, response.contentType())
        }
    }

    @Test
    fun testStatus404CustomObject() = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, code ->
                    call.respondText("${code.value} ${code.description}", status = code)
                }
            }

            intercept(ApplicationCallPipeline.Call) {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override val status = HttpStatusCode.NotFound
                        override fun readFrom(): ByteReadChannel = fail("Should never reach here")
                    }
                )
            }
        }

        client.get("/missing").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("404 ${HttpStatusCode.NotFound.description}", response.bodyAsText())
            assertEquals(textPlainUtf8, response.contentType())
        }
    }

    @Test
    fun testStatus404WithInterceptor() {
        class O

        testApplication {
            application {
                install(StatusPages) {
                    status(HttpStatusCode.NotFound) { call, code ->
                        call.respondText("${code.value} ${code.description}", status = code)
                    }
                }

                intercept(ApplicationCallPipeline.Plugins) {
                    call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                        if (message is O) proceedWith(HttpStatusCode.NotFound)
                    }
                }

                routing {
                    get("/") {
                        call.respond(O())
                    }
                }
            }

            client.get("/").let { response ->
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertEquals("404 ${HttpStatusCode.NotFound.description}", response.bodyAsText())
            }
        }
    }

    @Test
    fun testFailPage() = testApplication {
        application {
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.InternalServerError)
                }
            }

            routing {
                get("/iae") {
                    throw IllegalArgumentException()
                }
                get("/npe") {
                    throw NullPointerException()
                }
            }
        }

        client.get("/iae").let { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("IllegalArgumentException", response.bodyAsText())
        }

        client.get("/npe").let { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("NullPointerException", response.bodyAsText())
        }
    }

    @Test
    fun testFailPageDuringInterceptor() {
        class O

        testApplication {
            application {
                intercept(ApplicationCallPipeline.Plugins) {
                    call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                        if (message is O) {
                            throw IllegalStateException()
                        }
                    }
                }

                install(StatusPages) {
                    exception<IllegalStateException> { call, cause ->
                        call.respondText(cause::class.simpleName!!, status = HttpStatusCode.InternalServerError)
                    }
                }

                routing {
                    get("/") {
                        call.respond(O())
                    }
                }
            }

            client.get("/").let { response ->
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals("IllegalStateException", response.bodyAsText())
            }
        }
    }

    @Test
    fun testErrorDuringStatus() = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { _, _ ->
                    throw IllegalStateException("")
                }
                exception<Throwable> { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.InternalServerError)
                }
            }
        }

        client.get("/").let { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("IllegalStateException", response.bodyAsText())
        }
    }

    @Test
    fun testErrorShouldNotRecurse() = testApplication {
        application {
            install(StatusPages) {
                exception<IllegalStateException> { _, _ ->
                    throw IllegalStateException()
                }
            }

            intercept(ApplicationCallPipeline.Fallback) {
                throw NullPointerException()
            }
        }

        assertFails {
            client.get("/")
        }
    }

    @Test
    fun testErrorFromExceptionContent() {
        class ValidationException(val code: String) : RuntimeException()

        testApplication {
            application {
                install(StatusPages) {
                    exception<ValidationException> { call, cause ->
                        // Can access `cause.code` without casting
                        call.respondText(cause.code, status = HttpStatusCode.InternalServerError)
                    }
                }

                routing {
                    get("/ve") {
                        throw ValidationException("code")
                    }
                }
            }

            client.get("/ve").let { response ->
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals("code", response.bodyAsText())
            }
        }
    }

    @Test
    fun testErrorInAsync(): Unit = testApplication {
        class AsyncFailedException : Exception()

        application {
            install(StatusPages) {
                exception<AsyncFailedException> { call, _ ->
                    call.respondText("Async failed")
                }
                exception<CancellationException> { call, _ ->
                    call.respondText("Cancelled")
                }
            }

            routing {
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
        }

        assertEquals("Async failed", client.get("/fail").bodyAsText())
        assertEquals("OK", client.get("/cancel").bodyAsText())
    }

    @Test
    fun testDefaultKtorExceptionWithoutPlugin(): Unit = testApplication {
        application {
            routing {
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
        }

        assertEquals(HttpStatusCode.BadRequest, client.get("/bad-request").status)
        assertEquals(HttpStatusCode.UnsupportedMediaType, client.get("/media-type-not-supported").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/not-found").status)
    }

    @Test
    fun testDefaultKtorExceptionWithPluginHandlingExceptions(): Unit = testApplication {
        application {
            install(StatusPages) {
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

            routing {
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
        }

        client.get("/bad-request").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("BadRequestException", response.bodyAsText())
        }
        client.get("/media-type-not-supported").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("UnsupportedMediaTypeException", response.bodyAsText())
        }
        client.get("/not-found").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("NotFoundException", response.bodyAsText())
        }
    }

    @Test
    fun testDefaultKtorExceptionWithPluginCustomStatusPages(): Unit = testApplication {
        application {
            install(StatusPages) {
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

            routing {
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
        }

        client.get("/bad-request").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("BadRequest", response.bodyAsText())
        }
        client.get("/media-type-not-supported").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("UnsupportedMediaType", response.bodyAsText())
        }
        client.get("/not-found").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("NotFound", response.bodyAsText())
        }
    }

    @Test
    fun testRoutingNotCalledAfterStatusPages() {
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
                    exception<NotFoundException> { call: ApplicationCall, _ ->
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
    fun testVerify500OnException() = testApplication {
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
