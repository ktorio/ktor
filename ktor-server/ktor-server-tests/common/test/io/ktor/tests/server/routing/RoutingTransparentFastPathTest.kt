/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** Stands in for `authenticate { }`: a transparent node whose children are merged into the parent. */
@OptIn(InternalAPI::class)
private class TransparentBlock : TransparentRouteSelector()

private fun Route.transparentBlock(block: Route.() -> Unit) {
    createChild(TransparentBlock()).apply(block)
}

class RoutingTransparentFastPathTest {

    @Test
    fun `constant in first transparent block wins over parameter in second block`() = testApplication {
        routing {
            transparentBlock { get("/articles/latest") { call.respondText("latest") } }
            transparentBlock { get("/articles/{id}") { call.respondText("id=" + call.parameters["id"]) } }
        }
        assertEquals("latest", client.get("/articles/latest").bodyAsText())
        assertEquals("id=5", client.get("/articles/5").bodyAsText())
    }

    @Test
    fun `constant in second transparent block wins over parameter in first block`() = testApplication {
        routing {
            transparentBlock { get("/articles/{id}") { call.respondText("id=" + call.parameters["id"]) } }
            transparentBlock { get("/articles/latest") { call.respondText("latest") } }
        }
        assertEquals("latest", client.get("/articles/latest").bodyAsText())
        assertEquals("id=5", client.get("/articles/5").bodyAsText())
    }

    @Test
    fun `constant and parameter in separate transparent blocks inside a nested route`() = testApplication {
        routing {
            route("/articles") {
                transparentBlock { get("latest") { call.respondText("latest") } }
                transparentBlock { get("{id}") { call.respondText("id=" + call.parameters["id"]) } }
            }
        }
        assertEquals("latest", client.get("/articles/latest").bodyAsText())
        assertEquals("id=5", client.get("/articles/5").bodyAsText())
    }

    @Test
    fun `same path with different methods in separate transparent blocks resolves both`() = testApplication {
        routing {
            transparentBlock { get("/articles/latest") { call.respondText("get") } }
            transparentBlock { post("/articles/latest") { call.respondText("post") } }
        }
        assertEquals("get", client.get("/articles/latest").bodyAsText())
        assertEquals("post", client.post("/articles/latest").bodyAsText())
    }
}
