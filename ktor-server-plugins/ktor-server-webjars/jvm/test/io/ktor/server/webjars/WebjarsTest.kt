/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.webjars

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class WebjarsTest {

    @Test
    fun resourceNotFound() {
        withTestApplication {
            application.install(Webjars)
            handleRequest(HttpMethod.Get, "/webjars/foo.js").let { call ->
                // Should be handled by some other routing
                assertEquals(HttpStatusCode.NotFound, call.response.status())
            }
        }
    }

    @Test
    fun pathLike() {
        withTestApplication {
            application.install(Webjars)
            application.routing {
                get("/webjars-something/jquery") {
                    call.respondText { "Something Else" }
                }
            }
            handleRequest(HttpMethod.Get, "/webjars-something/jquery").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Something Else", call.response.content)
            }
        }
    }

    @Test
    fun nestedPath() {
        withTestApplication {
            application.install(Webjars) {
                path = "/assets/webjars"
            }
            handleRequest(HttpMethod.Get, "/assets/webjars/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun rootPath() {
        withTestApplication {
            application.install(Webjars) {
                path = "/"
            }
            handleRequest(HttpMethod.Get, "/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun rootPath2() {
        withTestApplication {
            application.install(Webjars) {
                path = "/"
            }
            application.routing {
                get("/") { call.respondText("Hello, World") }
            }
            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("Hello, World", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun versionAgnostic() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun withGetParameters() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/jquery.js?param1=value1").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun withSpecificVersion() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/3.3.1/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun withConditionalHeaders() {
        withTestApplication {
            application.install(Webjars)
            application.install(ConditionalHeaders)
            handleRequest(HttpMethod.Get, "/webjars/jquery/3.3.1/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
                assertNotNull(call.response.headers["Last-Modified"])
            }
        }
    }
}
