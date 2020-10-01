/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class XMethodOverrideTest {
    @Test
    fun testNoFeature(): Unit = withTestApplication<Unit> {
        application.intercept(ApplicationCallPipeline.Call) {
            assertEquals(HttpMethod.Get, call.request.origin.method)
            assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

            call.respondText(call.request.origin.method.value)
        }

        assertEquals("GET", handleRequest(HttpMethod.Get, "/", {
            addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
        }).response.content)
    }

    @Test
    fun testWithFeatureMethodDelete(): Unit = withTestApplication {
        application.install(XHttpMethodOverrideSupport)

        application.intercept(ApplicationCallPipeline.Call) {
            assertEquals(HttpMethod.Delete, call.request.origin.method)
            assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

            call.respondText(call.request.origin.method.value)
        }

        assertEquals("DELETE", handleRequest(HttpMethod.Get, "/", {
            addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
        }).response.content)
    }

    @Test
    fun testWithFeatureMethodDeleteRouting(): Unit = withTestApplication {
        application.install(XHttpMethodOverrideSupport)

        application.routing {
            delete("/") {
                call.respondText("1")
            }
            get("/") {
                call.respondText("2")
            }
        }

        assertEquals("1", handleRequest(HttpMethod.Get, "/", {
            addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
        }).response.content)

        assertEquals("2", handleRequest(HttpMethod.Get, "/").response.content)
    }

    @Test
    fun testWithFeatureMethodPatch(): Unit = withTestApplication {
        application.install(XHttpMethodOverrideSupport)

        application.intercept(ApplicationCallPipeline.Call) {
            assertEquals(HttpMethod.Patch, call.request.origin.method)
            assertEquals("PATCH", call.request.header(HttpHeaders.XHttpMethodOverride))

            call.respondText(call.request.origin.method.value)
        }

        assertEquals("PATCH", handleRequest(HttpMethod.Post, "/", {
            addHeader(HttpHeaders.XHttpMethodOverride, "PATCH")
        }).response.content)
    }

    @Test
    fun testWithFeatureCustomHeaderName(): Unit = withTestApplication {
        application.install(XHttpMethodOverrideSupport) {
            headerName = "X-My-Header"
        }

        application.intercept(ApplicationCallPipeline.Call) {
            assertEquals(HttpMethod.Get, call.request.origin.method)
            assertEquals("DELETE", call.request.header(HttpHeaders.XHttpMethodOverride))

            call.respondText(call.request.origin.method.value)
        }

        assertEquals("GET", handleRequest(HttpMethod.Get, "/", {
            addHeader(HttpHeaders.XHttpMethodOverride, "DELETE")
            addHeader("X-My-Header", "GET")
        }).response.content)
    }
}
