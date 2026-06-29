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

/**
 * Verifies that the constant-path routing fast path produces the same observable behavior as
 * the regular DFS resolver across a range of common routing shapes.
 */
class RoutingFastPathTest {

    @Test
    fun simpleConstantPathResolves() = testApplication {
        routing {
            get("/hello") { call.respondText("hello") }
        }
        val response = client.get("/hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
    }

    @Test
    fun nestedConstantPathResolves() = testApplication {
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
    fun unknownConstantPathReturnsNotFound() = testApplication {
        routing {
            get("/hello") { call.respondText("hi") }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/world").status)
    }

    @Test
    fun unsupportedMethodReturnsMethodNotAllowed() = testApplication {
        routing {
            get("/hello") { call.respondText("hi") }
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, client.post("/hello").status)
    }

    @Test
    fun multipleMethodsOnSamePathBothResolve() = testApplication {
        routing {
            get("/r") { call.respondText("g") }
            post("/r") { call.respondText("p") }
        }
        assertEquals("g", client.get("/r").bodyAsText())
        assertEquals("p", client.post("/r").bodyAsText())
    }

    @Test
    fun constantSiblingTakesPrecedenceOverPathParameter() = testApplication {
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
    fun constantSiblingCoexistsWithTailcard() = testApplication {
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
    fun transparentWrapperKeepsCorrectResolution() = testApplication {
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
    fun parentPipelineStillExecutesForFastPath() = testApplication {
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
    fun repeatedRequestsHitCachedTrie() = testApplication {
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
    fun percentEncodedPathFallsBackToSlowPath() = testApplication {
        // The fast path must defer to the slow resolver when the request path contains a
        // percent-encoded byte, so that decoded equality is honoured: `/hi%20there`
        // (i.e. "hi there") must match a route registered as `/hi there`.
        routing {
            get("/hi there") { call.respondText("hello") }
        }
        val response = client.get("/hi%20there")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
    }

    @Test
    fun parameterRouteResolvesViaFastPath() = testApplication {
        routing {
            get("/users/{id}") {
                call.respondText("user=${call.parameters["id"]}")
            }
        }
        assertEquals("user=42", client.get("/users/42").bodyAsText())
        assertEquals("user=abc", client.get("/users/abc").bodyAsText())
    }

    @Test
    fun parameterRouteMissingRequiredSegmentFallsBack() = testApplication {
        // `GET /users/` (trailing slash) must NOT match `get("/users/{id}")` because the
        // non-optional parameter rejects empty segments. The fast path needs to bail out so
        // that the slow path produces a 404.
        routing {
            get("/users/{id}") { call.respondText("user=${call.parameters["id"]}") }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/users/").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/users").status)
    }

    @Test
    fun multipleParametersInSamePathResolve() = testApplication {
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
    fun parameterRouteDoesNotShadowSibling() = testApplication {
        // Re-checks the constant-vs-parameter sibling ordering for the parameter fast path.
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
    fun parameterRouteWithPrefixSuffixDefersToSlowPath() = testApplication {
        // `{id}.html`-style parameter selectors carry a prefix/suffix and are deliberately not
        // handled by the trie fast path. They must still resolve correctly via the slow path.
        routing {
            get("/items/{id}.html") { call.respondText("html=${call.parameters["id"]}") }
        }
        assertEquals("html=42", client.get("/items/42.html").bodyAsText())
    }

    @Test
    fun trailingSlashDefersToSlowPath() = testApplication {
        // The fast path must defer to the slow resolver for trailing-slash URLs so that
        // strict trailing-slash semantics (separate `/foo` and `/foo/` routes) keep working.
        routing {
            get("/foo") { call.respondText("foo") }
            get("/foo/") { call.respondText("foo-slash") }
        }
        assertEquals("foo", client.get("/foo").bodyAsText())
        assertEquals("foo-slash", client.get("/foo/").bodyAsText())
    }

    @Test
    fun constantSiblingResolvesAlongsideRootLevelTailcard() = testApplication {
        // Regression test for the benchmark scenario: a wildcard / tailcard catch-all at the
        // routing root (as installed by `staticResources("")` in the user's hello-world
        // benchmark) must not stop the constant `/hello` and `/clear` endpoints from
        // resolving via the fast path. A constant trie hit (quality 1.0) always outranks a
        // root-level tailcard (quality ≤ 0.5), so the fast path is safe.
        routing {
            // Stand-in for `staticResources("")` — same selector shape: a `{path...}`
            // tailcard at the routing root that responds 404 by default.
            get("/{path...}") { call.respondText("static:${call.parameters.getAll("path")}") }

            get("/hello") { call.respondText("Hello, World!") }
            get("/clear") { call.respondText("clear") }
        }
        // Hot path: must resolve through the trie regardless of the tailcard sibling.
        assertEquals("Hello, World!", client.get("/hello").bodyAsText())
        assertEquals("clear", client.get("/clear").bodyAsText())
        // Slow-path fallback: paths not covered by the constant trie still resolve correctly
        // against the tailcard.
        assertEquals("static:[some, other]", client.get("/some/other").bodyAsText())
    }

    @Test
    fun queryParameterSiblingDefersToSlowPath() = testApplication {
        // Query/header parameter selectors have `qualityConstant` (1.0), the same as a
        // constant path match. Tie-breaking is by route registration order, which the trie
        // cannot model — so the trie must mark the parent node ambiguous and defer the
        // entire subtree to the slow DFS.
        routing {
            route("/test") {
                param("p") { handle { call.respondText("param") } }
                get { call.respondText("get") }
            }
        }
        assertEquals("param", client.get("/test?p=v").bodyAsText())
        assertEquals("get", client.get("/test").bodyAsText())
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
