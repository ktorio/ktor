/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RoutingFastPathTest {

    @Test
    fun `simple constant path resolves`() = testApplication {
        routing {
            get("/hello") { call.respondText("hello") }
        }
        val response = client.get("/hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
    }

    @Test
    fun `nested constant path resolves`() = testApplication {
        routing {
            route("/api") {
                route("/v1") {
                    get("/ping") { call.respondText("pong") }
                }
            }
        }
        val response = client.get("/api/v1/ping")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("pong", response.bodyAsText())
    }

    @Test
    fun `unknown constant path returns not found`() = testApplication {
        routing {
            get("/hello") { call.respondText("hi") }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/world").status)
    }

    @Test
    fun `unsupported method returns method not allowed`() = testApplication {
        routing {
            get("/hello") { call.respondText("hi") }
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, client.post("/hello").status)
    }

    @Test
    fun `multiple methods on same path both resolve`() = testApplication {
        routing {
            get("/r") { call.respondText("g") }
            post("/r") { call.respondText("p") }
        }
        assertEquals("g", client.get("/r").bodyAsText())
        assertEquals("p", client.post("/r").bodyAsText())
    }

    @Test
    fun `constant sibling takes precedence over path parameter`() = testApplication {
        routing {
            route("/users") {
                get("/me") { call.respondText("me") }
                get("/{id}") {
                    val id = call.parameters["id"]
                    call.respondText("id=$id")
                }
            }
        }
        assertEquals("me", client.get("/users/me").bodyAsText())
        assertEquals("id=42", client.get("/users/42").bodyAsText())
    }

    @Test
    fun `constant sibling coexists with tailcard`() = testApplication {
        routing {
            get("/files/list") { call.respondText("list") }
            get("/files/{path...}") {
                val parts = call.parameters.getAll("path")
                call.respondText("path=$parts")
            }
        }
        assertEquals("list", client.get("/files/list").bodyAsText())
        assertEquals("path=[a, b, c]", client.get("/files/a/b/c").bodyAsText())
    }

    @Test
    fun `transparent wrapper keeps correct resolution`() = testApplication {
        routing {
            route("/secured") {
                transparent {
                    get { call.respondText("ok") }
                }
            }
        }
        val response = client.get("/secured")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `parent pipeline still executes for fast path`() = testApplication {
        var interceptorRan = false
        routing {
            route("/v1") {
                intercept(ApplicationCallPipeline.Plugins) { interceptorRan = true }
                get("/ping") { call.respondText("pong") }
            }
        }
        assertEquals("pong", client.get("/v1/ping").bodyAsText())
        assertTrue(interceptorRan, "Ancestor interceptor must run on fast-path-resolved calls")
    }

    @Test
    fun `repeated requests hit cached trie`() = testApplication {
        routing {
            get("/a") { call.respondText("A") }
            get("/b") { call.respondText("B") }
        }
        repeat(10) {
            assertEquals("A", client.get("/a").bodyAsText())
            assertEquals("B", client.get("/b").bodyAsText())
        }
    }

    @Test
    fun `percent encoded path falls back to slow path`() = testApplication {
        routing {
            get("/hi there") { call.respondText("hello") }
        }
        val response = client.get("/hi%20there")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
    }

    @Test
    fun `parameter route resolves via fast path`() = testApplication {
        routing {
            get("/users/{id}") {
                call.respondText("user=${call.parameters["id"]}")
            }
        }
        assertEquals("user=42", client.get("/users/42").bodyAsText())
        assertEquals("user=abc", client.get("/users/abc").bodyAsText())
    }

    @Test
    fun `parameter route missing required segment falls back`() = testApplication {
        routing {
            get("/users/{id}") { call.respondText("user=${call.parameters["id"]}") }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/users/").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/users").status)
    }

    @Test
    fun `multiple parameters in same path resolve`() = testApplication {
        routing {
            get("/users/{userId}/posts/{postId}") {
                val userId = call.parameters["userId"]
                val postId = call.parameters["postId"]
                call.respondText("u=$userId,p=$postId")
            }
        }
        assertEquals("u=10,p=20", client.get("/users/10/posts/20").bodyAsText())
    }

    @Test
    fun `parameter route does not shadow sibling`() = testApplication {
        routing {
            route("/x") {
                get("/static") { call.respondText("static") }
                get("/{any}") { call.respondText("param=${call.parameters["any"]}") }
            }
        }
        assertEquals("static", client.get("/x/static").bodyAsText())
        assertEquals("param=other", client.get("/x/other").bodyAsText())
    }

    @Test
    fun `parameter route with prefix suffix defers to slow path`() = testApplication {
        routing {
            get("/items/{id}.html") { call.respondText("html=${call.parameters["id"]}") }
        }
        assertEquals("html=42", client.get("/items/42.html").bodyAsText())
    }

    @Test
    fun `trailing slash defers to slow path`() = testApplication {
        routing {
            get("/foo") { call.respondText("foo") }
            get("/foo/") { call.respondText("foo-slash") }
        }
        assertEquals("foo", client.get("/foo").bodyAsText())
        assertEquals("foo-slash", client.get("/foo/").bodyAsText())
    }

    @Test
    fun `constant sibling resolves alongside root level tailcard`() = testApplication {
        // Regression: a root-level tailcard (e.g. from staticResources("")) must not prevent
        // constant-path routes from resolving via the fast path.
        routing {
            get("/{path...}") { call.respondText("static:${call.parameters.getAll("path")}") }
            get("/hello") { call.respondText("Hello, World!") }
            get("/clear") { call.respondText("clear") }
        }
        assertEquals("Hello, World!", client.get("/hello").bodyAsText())
        assertEquals("clear", client.get("/clear").bodyAsText())
        assertEquals("static:[some, other]", client.get("/some/other").bodyAsText())
    }

    @Test
    fun `query parameter sibling defers to slow path`() = testApplication {
        routing {
            route("/test") {
                param("p") { handle { call.respondText("param") } }
                get { call.respondText("get") }
            }
        }
        assertEquals("param", client.get("/test?p=v").bodyAsText())
        assertEquals("get", client.get("/test").bodyAsText())
    }

    @Test
    fun `constant route rejects trailing slash by default`() = testApplication {
        // A constant route without a trailing-slash variant must not match a trailing-slash
        // request under default (strict) routing — the fast path must defer to DFS.
        routing {
            get("/bar") { call.respondText("bar") }
        }
        assertEquals("bar", client.get("/bar").bodyAsText())
        assertFalse(client.get("/bar/").status.isSuccess())
    }

    @Test
    fun `constant route matches trailing slash when ignored`() = testApplication {
        install(IgnoreTrailingSlash)
        routing {
            get("/bar") { call.respondText("bar") }
        }
        assertEquals("bar", client.get("/bar").bodyAsText())
        assertEquals("bar", client.get("/bar/").bodyAsText())
    }

    @Test
    fun `wildcard segment resolves via fast path`() = testApplication {
        routing {
            get("/files/*") { call.respondText("wild:${call.parameters.entries()}") }
        }
        // Wildcard matches a single segment and captures nothing.
        assertEquals("wild:[]", client.get("/files/anything").bodyAsText())
        assertEquals("wild:[]", client.get("/files/42").bodyAsText())
    }

    @Test
    fun `wildcard does not match missing or extra segments`() = testApplication {
        routing {
            get("/files/*") { call.respondText("wild") }
        }
        // A single wildcard consumes exactly one segment.
        assertEquals(HttpStatusCode.NotFound, client.get("/files").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/files/a/b").status)
    }

    @Test
    fun `constant sibling takes precedence over wildcard`() = testApplication {
        routing {
            route("/files") {
                get("/list") { call.respondText("list") }
                get("/*") { call.respondText("wild") }
            }
        }
        assertEquals("list", client.get("/files/list").bodyAsText())
        assertEquals("wild", client.get("/files/other").bodyAsText())
    }

    @Test
    fun `wildcard in the middle resolves via fast path`() = testApplication {
        routing {
            get("/a/*/b") { call.respondText("mid") }
        }
        assertEquals("mid", client.get("/a/x/b").bodyAsText())
        assertEquals("mid", client.get("/a/anything/b").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/a/x/c").status)
    }

    @Test
    fun `tailcard captures remaining segments via fast path`() = testApplication {
        routing {
            get("/files/{path...}") { call.respondText("path=${call.parameters.getAll("path")}") }
        }
        assertEquals("path=[a, b, c]", client.get("/files/a/b/c").bodyAsText())
        assertEquals("path=[only]", client.get("/files/only").bodyAsText())
    }

    @Test
    fun `tailcard decodes percent encoded segments`() = testApplication {
        routing {
            get("/files/{path...}") { call.respondText("path=${call.parameters.getAll("path")}") }
        }
        assertEquals("path=[a b, c]", client.get("/files/a%20b/c").bodyAsText())
    }

    @Test
    fun `anonymous tailcard captures nothing`() = testApplication {
        routing {
            get("/files/{...}") { call.respondText("anon:${call.parameters.entries()}") }
        }
        assertEquals("anon:[]", client.get("/files/a/b/c").bodyAsText())
    }

    @Test
    fun `parameter and tailcard siblings defer to slow path`() = testApplication {
        // Two competing non-constant matchers at the same node: the greedy tree must defer,
        // but resolution must still be correct via DFS.
        routing {
            route("/x") {
                get("/{id}") { call.respondText("id=${call.parameters["id"]}") }
                get("/{rest...}") { call.respondText("rest=${call.parameters.getAll("rest")}") }
            }
        }
        // Single segment: the higher-quality plain parameter wins.
        assertEquals("id=one", client.get("/x/one").bodyAsText())
        // Multiple segments: only the tailcard matches.
        assertEquals("rest=[a, b]", client.get("/x/a/b").bodyAsText())
    }

    @Test
    fun `optional parameter defers to slow path`() = testApplication {
        routing {
            get("/opt/{id?}") { call.respondText("id=${call.parameters["id"]}") }
        }
        assertEquals("id=42", client.get("/opt/42").bodyAsText())
        assertEquals("id=null", client.get("/opt").bodyAsText())
    }

    @Test
    fun `prefixed tailcard defers to slow path`() = testApplication {
        routing {
            get("/files/pre{rest...}") { call.respondText("rest=${call.parameters.getAll("rest")}") }
        }
        assertEquals("rest=[fix, a, b]", client.get("/files/prefix/a/b").bodyAsText())
    }

    @Test
    fun `trailing slash against tailcard resolves correctly`() = testApplication {
        routing {
            get("/files/{path...}") { call.respondText("path=${call.parameters.getAll("path")}") }
        }
        assertEquals("path=[a, b]", client.get("/files/a/b").bodyAsText())
        // Trailing slash is deferred to DFS; resolution must remain correct.
        assertEquals(HttpStatusCode.OK, client.get("/files/a/b/").status)
    }

    private fun Route.transparent(build: Route.() -> Unit): Route {
        val route = createChild(
            object : RouteSelector() {
                override suspend fun evaluate(
                    context: RoutingResolveContext,
                    segmentIndex: Int,
                ): RouteSelectorEvaluation =
                    RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityTransparent)
            }
        )
        route.build()
        return route
    }
}
