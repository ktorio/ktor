/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

// `Common` class contains base tests for all platforms
// on JVM we also have more tests
public expect abstract class HttpServerTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : CommonHttpServerTestSuite<TEngine, TConfiguration>

public abstract class CommonHttpServerTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    public fun testRedirect() {
        createAndStartServer {
            handle {
                call.respondRedirect("http://localhost:${call.request.port()}/page", true)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, status.value)
        }
    }

    @Test
    public fun testRedirectFromInterceptor() {
        createAndStartServer {
            application.intercept(ApplicationCallPipeline.Features) {
                call.respondRedirect("/2", true)
            }
        }

        withUrl("/1/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, status.value)

            assertEquals("/2", headers[HttpHeaders.Location])
        }
    }

    @Test
    public fun testHeader() {
        createAndStartServer {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "test-etag")
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("test-etag", headers[HttpHeaders.ETag])
            assertNull(headers[HttpHeaders.TransferEncoding])
            assertEquals("5", headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    public open fun testHeadRequest() {
        createAndStartServer {
            install(AutoHeadResponse)
            handle {
                call.respondText("Hello")
            }
        }

        withUrl("/", { method = HttpMethod.Head }) {
            assertEquals(200, status.value)
            assertNull(headers[HttpHeaders.TransferEncoding])
            assertEquals("5", headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    public fun testCookie() {
        createAndStartServer {
            handle {
                call.response.cookies.append("k1", "v1")
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("k1=v1; \$x-enc=URI_ENCODING", headers[HttpHeaders.SetCookie])
        }
    }

    @Test
    public fun testPathComponentsDecoding() {
        createAndStartServer {
            get("/a%20b") {
                call.respondText("space")
            }
            get("/a+b") {
                call.respondText("plus")
            }
        }

        withUrl("/a%20b") {
            assertEquals(200, status.value)
            assertEquals("space", bodyAsText())
        }
        withUrl("/a+b") {
            assertEquals(200, status.value)
            assertEquals("plus", bodyAsText())
        }
    }

    @Test
    public fun testFormUrlEncoded() {
        createAndStartServer {
            post("/") {
                call.respondText("${call.parameters["urlp"]},${call.receiveParameters()["formp"]}")
            }
        }

        withUrl(
            "/?urlp=1",
            {
                method = HttpMethod.Post
                setBody(ByteArrayContent("formp=2".toByteArray(), ContentType.Application.FormUrlEncoded))
            }
        ) {
            assertEquals(HttpStatusCode.OK.value, status.value)
            assertEquals("1,2", bodyAsText())
        }
    }

    @Test
    public fun testRequestTwiceNoKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respondText("Text")
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "close")
            }
        ) {
            assertEquals("Text", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "close")
            }
        ) {
            assertEquals("Text", bodyAsText())
        }
    }

    @Test
    public fun testRequestTwiceWithKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respondText("Text")
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "keep-alive")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Text", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.Connection, "keep-alive")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Text", bodyAsText())
        }
    }

    @Test
    public fun test404() {
        createAndStartServer {
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }

        withUrl("/aaaa") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }
    }

    @Test
    public fun testRemoteHost() {
        createAndStartServer {
            handle {
                call.respondText {
                    call.request.local.remoteHost
                }
            }
        }

        withUrl("/") {
            bodyAsText().also { text ->
                assertNotNull(
                    listOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1").find {
                        it == text
                    }
                )
            }
        }
    }

    @Test
    public fun testRequestParameters() {
        createAndStartServer {
            get("/*") {
                call.respond(call.request.queryParameters.getAll(call.request.path().removePrefix("/")).toString())
            }
        }

        withUrl("/single?single=value") {
            assertEquals("[value]", bodyAsText())
        }
        withUrl("/multiple?multiple=value1&multiple=value2") {
            assertEquals("[value1, value2]", bodyAsText())
        }
        withUrl("/missing") {
            assertEquals("null", bodyAsText())
        }
    }

    @Test
    public fun testStatusCodeDirect() {
        createAndStartServer {
            get("/") {
                call.response.status(HttpStatusCode.Found)
                call.respond("Hello")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    public fun testStatusCodeViaResponseObject() {
        var completed = false
        createAndStartServer {
            get("/") {
                call.respond(HttpStatusCode.Found)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, status.value)
            completed = true
        }
        assertTrue(completed)
    }

    @Test
    public fun testProxyHeaders() {
        createAndStartServer {
            install(XForwardedHeaderSupport)
            get("/") {
                call.respond(call.url { })
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host:90")
            }
        ) { port ->
            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host:90/", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host")
            }
        ) { port ->
            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host/", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host:90")
                header(HttpHeaders.XForwardedProto, "https")
            }
        ) {
            assertEquals("https://my-host:90/", bodyAsText())
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.XForwardedHost, "my-host")
                header(HttpHeaders.XForwardedProto, "https")
            }
        ) {
            assertEquals("https://my-host/", bodyAsText())
        }
    }

    @Test
    public fun testRequestParts() {
        createAndStartServer {
            get("/path/1") {
                call.respond(call.request.path())
            }
            get("/document/1") {
                call.respond(call.request.document())
            }
            get("/queryString/1") {
                call.respond(call.request.queryString())
            }
            get("/uri/1") {
                call.respond(call.request.uri)
            }
        }

        withUrl("/path/1?p=v") {
            assertEquals("/path/1", bodyAsText())
        }
        withUrl("/path/1?") {
            assertEquals("/path/1", bodyAsText())
        }
        withUrl("/path/1") {
            assertEquals("/path/1", bodyAsText())
        }

        withUrl("/document/1?p=v") {
            assertEquals("1", bodyAsText())
        }
        withUrl("/document/1?") {
            assertEquals("1", bodyAsText())
        }
        withUrl("/document/1") {
            assertEquals("1", bodyAsText())
        }

        withUrl("/queryString/1?p=v") {
            assertEquals("p=v", bodyAsText())
        }
        withUrl("/queryString/1?") {
            assertEquals("", bodyAsText())
        }
        withUrl("/queryString/1") {
            assertEquals("", bodyAsText())
        }

        withUrl("/uri/1?p=v") {
            assertEquals("/uri/1?p=v", bodyAsText())
        }
        withUrl("/uri/1?") {
            assertEquals("/uri/1?", bodyAsText())
        }
        withUrl("/uri/1") {
            assertEquals("/uri/1", bodyAsText())
        }
    }

    @Test
    public fun testHeadersReturnCorrectly() {
        createAndStartServer {
            get("/") {
                assertEquals("foo", call.request.headers["X-Single-Value"])
                assertEquals("foo,bar", call.request.headers["X-Double-Value"])

                assertNull(call.request.headers["X-Nonexistent-Header"])
                assertNull(call.request.headers.getAll("X-Nonexistent-Header"))

                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        withUrl(
            "/",
            {
                headers {
                    append("X-Single-Value", "foo")
                    append("X-Double-Value", "foo")
                    append("X-Double-Value", "bar")
                }
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("OK", bodyAsText())
        }
    }

    @Test
    public fun testParentContextPropagates() {
        createAndStartServer(
            parent = TestData("parent")
        ) {
            get("/") {
                val valueFromContext = coroutineContext[TestData]!!.name
                call.respond(HttpStatusCode.OK, valueFromContext)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("parent", bodyAsText())
        }
    }

    private data class TestData(
        val name: String
    ) : AbstractCoroutineContextElement(TestData) {
        /**
         * Key for [CoroutineName] instance in the coroutine context.
         */
        public companion object Key : CoroutineContext.Key<TestData>
    }
}
