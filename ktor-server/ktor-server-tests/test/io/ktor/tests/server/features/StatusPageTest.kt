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
            application.intercept(ApplicationCallPipeline.Fallback) {
                call.respond(HttpStatusCode.NotFound)
            }

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
            application.intercept(ApplicationCallPipeline.Fallback) {
                call.respond(HttpStatusCode.NotFound)
            }

            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) {
                    call.respondText("${it.value} ${it.description}", status = it)
                }
            }

            application.intercept(ApplicationCallPipeline.Infrastructure) {
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
            application.intercept(ApplicationCallPipeline.Infrastructure) {
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

            application.intercept(ApplicationCallPipeline.Fallback) {
                call.respond(HttpStatusCode.NotFound)
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
}