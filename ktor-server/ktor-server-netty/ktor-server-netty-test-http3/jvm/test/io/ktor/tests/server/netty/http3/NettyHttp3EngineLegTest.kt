/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.httpVersion
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.test.base.EngineTestBase
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the `withUrl` HTTP/3 leg reaches the server over HTTP/3 and maps requests and
 * responses faithfully, so the shared engine suites can be layered on top of it.
 *
 * Every assertion here is about the leg itself, not about HTTP/3 server behaviour: the routes
 * report what the server observed, and the test checks that it matches what the client sent.
 */
class NettyHttp3EngineLegTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `the leg reaches the server over http3`() = runTest {
        createAndStartServer {
            get("/version") { call.respondText(call.request.httpVersion) }
        }

        withUrl("/version") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("HTTP/3", bodyAsText(), "the request must have been served over HTTP/3")
            assertEquals(HttpProtocolVersion.HTTP_3_0, version)
        }
    }

    @Test
    fun `request headers and query parameters survive the leg`() = runTest {
        createAndStartServer {
            get("/echo") {
                val name = call.request.queryParameters["name"]
                val custom = call.request.headers["X-Custom-Request"]
                call.response.headers.append("X-Custom-Response", "response-value")
                call.respondText("$name|$custom")
            }
        }

        withUrl("/echo?name=Ktor", { headers.append("X-Custom-Request", "request-value") }) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Ktor|request-value", bodyAsText())
            assertEquals("response-value", headers["X-Custom-Response"])
        }
    }

    @Test
    fun `request body is delivered through the leg`() = runTest {
        createAndStartServer {
            post("/upload") { call.respondText(call.receiveText()) }
        }

        val body = "posted over HTTP/3"
        withUrl("/upload", {
            method = HttpMethod.Post
            setBody(body)
        }) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(body, bodyAsText())
        }
    }

    @Test
    fun `a multi frame response body is reassembled`() = runTest {
        val payload = "0123456789".repeat(50_000) // 500 KB: spans many DATA frames

        createAndStartServer {
            get("/large") { call.respondText(payload) }
        }

        withUrl("/large") {
            assertEquals(HttpStatusCode.OK, status)
            val received = bodyAsText()
            assertEquals(payload.length, received.length, "response body was truncated")
            assertEquals(payload, received)
        }
    }

    @Test
    fun `status codes are propagated`() = runTest {
        createAndStartServer {
            get("/exists") { call.respondText("found") }
        }

        withUrl("/missing") {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }
}
