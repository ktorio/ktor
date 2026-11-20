/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.exceptionboundaries

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class ExceptionBoundariesTest {
    private open class DbException(message: String = "db") : RuntimeException(message)
    private abstract class RetryableDbException(message: String) : DbException(message)
    private class DbTimeout : RetryableDbException("timeout")
    private class Unlisted : RuntimeException("secret details")
    private class Wrapper(cause: Throwable) : RuntimeException(cause)
    private class Timeout : CancellationException("timeout")

    @Test
    fun `on matches subtypes`() = testApplication {
        val handler = exceptionHandler {
            on<RetryableDbException> {
                call.respondText("retryable", status = HttpStatusCode.ServiceUnavailable)
            }
            on<DbException> { call.respondText("db") }
        }
        routing {
            exceptionBoundary(handler) {
                get("/timeout") { throw DbTimeout() }
                get("/db") { throw DbException() }
            }
        }

        assertEquals("retryable", client.get("/timeout").bodyAsText())
        assertEquals("db", client.get("/db").bodyAsText())
    }

    @Test
    fun `the first matching handler wins`() = testApplication {
        val handler = exceptionHandler {
            on<DbException> { call.respondText("general") }
            on<DbTimeout> { call.respondText("specific") }
        }
        routing {
            exceptionBoundary(handler) {
                get("/") { throw DbTimeout() }
            }
        }

        assertEquals("general", client.get("/").bodyAsText())
    }

    @Test
    fun `handlers of one boundary are joined in the order given`() = testApplication {
        val first = exceptionHandler { on<DbException> { call.respondText("first") } }
        val second = exceptionHandler {
            on<DbException> { call.respondText("second") }
            on<IllegalStateException> { call.respondText("second state") }
        }
        routing {
            exceptionBoundary(first, second) {
                get("/db") { throw DbException() }
                get("/state") { error("state") }
            }
        }

        assertEquals("first", client.get("/db").bodyAsText())
        assertEquals("second state", client.get("/state").bodyAsText())
    }

    @Test
    fun `the inner boundary is checked first and unlisted exceptions go outward`() = testApplication {
        application {
            globalExceptionBoundary {
                on<IllegalStateException> { call.respondText("global") }
            }
        }
        routing {
            exceptionBoundary(
                exceptionHandler {
                    on<DbException> { call.respondText("outer") }
                    on<IllegalArgumentException> { call.respondText("outer argument") }
                }
            ) {
                exceptionBoundary(exceptionHandler { on<DbException> { call.respondText("inner") } }) {
                    get("/db") { throw DbException() }
                    get("/argument") { throw IllegalArgumentException() }
                    get("/state") { error("state") }
                }
            }
        }

        assertEquals("inner", client.get("/db").bodyAsText())
        assertEquals("outer argument", client.get("/argument").bodyAsText())
        assertEquals("global", client.get("/state").bodyAsText())
    }

    @Test
    fun `boundaries on the same parent are independent`() = testApplication {
        routing {
            exceptionBoundary(exceptionHandler { on<DbException> { call.respondText("a") } }) {
                get("/a") { throw DbException() }
            }
            exceptionBoundary(exceptionHandler { on<DbException> { call.respondText("b") } }) {
                get("/b") { throw DbException() }
            }
        }

        assertEquals("a", client.get("/a").bodyAsText())
        assertEquals("b", client.get("/b").bodyAsText())
    }

    @Test
    fun `causes are not unwrapped`() = testApplication {
        routing {
            exceptionBoundary(exceptionHandler { on<DbException> { call.respondText("db") } }) {
                get("/") { throw Wrapper(DbException()) }
            }
        }

        assertEquals(HttpStatusCode.InternalServerError, client.get("/").status)
    }

    @Test
    fun `lookups are cached per exception class`() {
        val entries = mutableListOf(
            CallHandlerEntry(DbException::class, skipSentCalls = true, handle = {}, function = "on")
        )
        val table = HandlerTable(entries)
        val entry = table.find(DbTimeout())
        assertNotNull(entry)

        entries.clear()

        assertSame(entry, table.find(DbTimeout()))
        assertNull(table.find(DbException()))
    }

    @Test
    fun `too broad types are rejected`() {
        assertFailsWith<IllegalArgumentException> { exceptionHandler { on<Throwable> { } } }
        assertFailsWith<IllegalArgumentException> { exceptionHandler { on<CancellationException> { } } }
        assertFailsWith<IllegalArgumentException> {
            exceptionHandler { onCallCoroutineFailure<Throwable> { _, _ -> } }
        }
        assertFailsWith<IllegalArgumentException> {
            exceptionHandler { onCallCoroutineFailure<CancellationException> { _, _ -> } }
        }
        assertFailsWith<IllegalArgumentException> {
            GlobalExceptionBoundaryBuilder(skipSentCalls = true).on<Throwable> { }
        }
        assertFailsWith<IllegalArgumentException> {
            GlobalExceptionBoundaryBuilder(skipSentCalls = true).onApplicationCoroutineFailure<CancellationException> {
                    _,
                    _
                ->
            }
        }
    }

    @Test
    fun `subclasses of too broad types are allowed`() = testApplication {
        exceptionHandler {
            on<Exception> { }
            on<Error> { }
            onCallCoroutineFailure<Exception> { _, _ -> }
        }
        routing {
            exceptionBoundary(exceptionHandler { on<Timeout> { call.respondText("timeout") } }) {
                get("/") { throw Timeout() }
            }
        }

        assertEquals("timeout", client.get("/").bodyAsText())
    }

    @Test
    fun `exceptionBoundary needs handlers`() = testApplication {
        routing {
            assertFailsWith<IllegalArgumentException> { exceptionBoundary { } }
        }
    }

    @Test
    fun `the handler and the outer boundaries are skipped after the response was sent`() = testApplication {
        var handled = 0
        application {
            globalExceptionBoundary { on<DbException> { handled++ } }
        }
        routing {
            exceptionBoundary(exceptionHandler { on<DbException> { handled++ } }) {
                exceptionBoundary(exceptionHandler { on<DbException> { handled++ } }) {
                    get("/") {
                        call.respondText("ok")
                        throw DbException()
                    }
                }
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
        assertEquals(0, handled)
    }

    @Test
    fun `skipSentCalls = false runs the handler after the response was sent`() = testApplication {
        val handled = CompletableDeferred<Boolean>()
        routing {
            exceptionBoundary(
                exceptionHandler(skipSentCalls = false) {
                    on<DbException> { handled.complete(call.response.isSent) }
                }
            ) {
                get("/") {
                    call.respondText("ok")
                    throw DbException()
                }
            }
        }

        assertEquals("ok", client.get("/").bodyAsText())
        assertTrue(withTimeout(5.seconds) { handled.await() })
    }

    @Test
    fun `global boundary blocks are merged in order`() = testApplication {
        application {
            globalExceptionBoundary { on<DbException> { call.respondText("first") } }
            globalExceptionBoundary {
                on<DbException> { call.respondText("second") }
                on<IllegalStateException> { call.respondText("second state") }
            }
        }
        routing {
            get("/db") { throw DbException() }
            get("/state") { error("state") }
        }

        assertEquals("first", client.get("/db").bodyAsText())
        assertEquals("second state", client.get("/state").bodyAsText())
    }

    @Test
    fun `global handlers get failures of plugins before routing`() = testApplication {
        application {
            install(
                createApplicationPlugin("Failing") {
                    onCall { call -> if (call.request.uri == "/plugin") throw DbException() }
                }
            )
            globalExceptionBoundary {
                on<DbException> { call.respondText("handled at ${call.route}", status = HttpStatusCode.BadGateway) }
            }
        }
        routing {
            get("/plugin") { call.respondText("unreachable") }
        }

        val response = client.get("/plugin")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals("handled at /", response.bodyAsText())
    }

    @Test
    fun `unhandled exceptions get a 500 with no body`() = testApplication {
        application { globalExceptionBoundary { } }
        routing {
            get("/") { throw Unlisted() }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `unhandled exceptions with a default status keep their message`() = testApplication {
        application { globalExceptionBoundary { } }
        routing {
            get("/bad") { throw BadRequestException("bad id") }
            get("/missing") { throw NotFoundException("no user") }
        }

        client.get("/bad").let { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("bad id", response.bodyAsText())
        }
        client.get("/missing").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("no user", response.bodyAsText())
        }
    }

    @Test
    fun `a scoped boundary alone gives the same fallback`() = testApplication {
        routing {
            exceptionBoundary(exceptionHandler { on<DbException> { call.respondText("db") } }) {
                get("/") { throw Unlisted() }
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `failures of children that fail the call reach on`() = testApplication {
        routing {
            exceptionBoundary(exceptionHandler { on<DbException> { call.respondText("db") } }) {
                get("/") {
                    coroutineScope { launch { throw DbException() } }
                    call.respondText("unreachable")
                }
            }
        }

        assertEquals("db", client.get("/").bodyAsText())
    }

    @Test
    fun `onCallCoroutineFailure gets failures of supervised children`() = testApplication {
        val failure = CompletableDeferred<Throwable>()
        routing {
            exceptionBoundary(
                exceptionHandler {
                    onCallCoroutineFailure<DbException> { e, _ -> failure.complete(e) }
                }
            ) {
                get("/") {
                    supervisorScope { launch { throw DbTimeout() } }
                    call.respondText("ok")
                }
            }
        }

        assertEquals("ok", client.get("/").bodyAsText())
        assertIs<DbTimeout>(withTimeout(5.seconds) { failure.await() })
    }

    @Test
    fun `unmatched call coroutine failures go to the outer boundary`() = testApplication {
        val failure = CompletableDeferred<String>()
        routing {
            exceptionBoundary(
                exceptionHandler {
                    onCallCoroutineFailure<DbException> { _, _ -> failure.complete("outer") }
                }
            ) {
                exceptionBoundary(
                    exceptionHandler {
                        onCallCoroutineFailure<IllegalStateException> { _, _ -> failure.complete("inner") }
                    }
                ) {
                    get("/") {
                        supervisorScope { launch { throw DbException() } }
                        call.respondText("ok")
                    }
                }
            }
        }

        assertEquals("ok", client.get("/").bodyAsText())
        assertEquals("outer", withTimeout(5.seconds) { failure.await() })
    }

    @Test
    fun `unmatched call coroutine failures go to onApplicationCoroutineFailure`() = testApplication {
        val failure = CompletableDeferred<Throwable>()
        application {
            globalExceptionBoundary {
                onApplicationCoroutineFailure<DbException> { e, _ -> failure.complete(e) }
            }
        }
        routing {
            exceptionBoundary(
                exceptionHandler { onCallCoroutineFailure<IllegalStateException> { _, _ -> } }
            ) {
                get("/") {
                    supervisorScope { launch { throw DbException() } }
                    call.respondText("ok")
                }
            }
        }

        assertEquals("ok", client.get("/").bodyAsText())
        assertIs<DbException>(withTimeout(5.seconds) { failure.await() })
    }

    @Test
    fun `onApplicationCoroutineFailure gets failures of application coroutines`() = testApplication {
        val failure = CompletableDeferred<Pair<Throwable, CoroutineName?>>()
        application {
            globalExceptionBoundary {
                onApplicationCoroutineFailure<DbException> { e, context ->
                    failure.complete(e to context[CoroutineName])
                }
            }
        }
        routing {
            get("/") {
                call.application.launch(CoroutineName("cleanup")) { throw DbException() }
                call.respondText("ok")
            }
        }

        assertEquals("ok", client.get("/").bodyAsText())
        val (exception, name) = withTimeout(5.seconds) { failure.await() }
        assertIs<DbException>(exception)
        assertEquals("cleanup", name?.name)
    }
}
