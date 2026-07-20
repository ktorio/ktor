/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * Regression tests for https://youtrack.jetbrains.com/issue/KTOR-613 /
 * https://github.com/ktorio/ktor/issues/1158.
 *
 * The routing resolution algorithm must always prefer the candidate with the highest *path*
 * match quality before any "transparent" selector - such as the one contributed by
 * [io.ktor.server.auth.AuthenticationRouteSelector] via `authenticate { ... }` - is allowed to
 * influence the outcome. In other words, wrapping a route in `authenticate { }` must never cause
 * it to outrank a sibling route that is a strictly better path match.
 *
 * These tests use an authentication provider that always rejects the request (no credentials are
 * ever sent), so a 401 response is a reliable signal that the routing engine incorrectly selected
 * the authenticated branch. A 200 response (or, where noted, an intentional 401) confirms the
 * expected candidate was chosen by path quality first.
 */
class RoutingResolutionQualityTest {

    private fun ApplicationTestBuilder.installAlwaysFailingBasicAuth(vararg names: String) {
        install(Authentication) {
            if (names.isEmpty()) {
                basic { validate { null } }
            } else {
                names.forEach { providerName -> basic(providerName) { validate { null } } }
            }
        }
    }

    @Test
    fun staticRouteWinsOverParameterRoute_noAuthInvolved() = testApplication {
        routing {
            get("/user") { call.respondText("static") }
            get("/{id}") { call.respondText("param") }
        }

        val response = client.get("/user")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static", response.bodyAsText())
    }

    @Test
    fun unauthenticatedStaticRouteWinsOverAuthenticatedParameterRoute() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            get("/user") { call.respondText("static") }
            authenticate {
                get("/{id}") { call.respondText("param") }
            }
        }

        // "/user" is a strictly better path match than "/{id}", so the unauthenticated static
        // route must win even though it was registered before the authenticated one and even
        // though no credentials are supplied.
        val response = client.get("/user")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static", response.bodyAsText())
    }

    @Test
    fun unauthenticatedStaticRouteWinsOverAuthenticatedParameterRoute_reverseRegistrationOrder() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            // Authenticated (lower quality) branch registered FIRST this time.
            authenticate {
                get("/{id}") { call.respondText("param") }
            }
            get("/user") { call.respondText("static") }
        }

        val response = client.get("/user")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static", response.bodyAsText())
    }

    @Test
    fun nestedAuthenticateBlocksDoNotOutrankUnauthenticatedStaticRoute() = testApplication {
        installAlwaysFailingBasicAuth("outer", "inner")

        routing {
            authenticate("outer") {
                authenticate("inner") {
                    get("/{id}") { call.respondText("param") }
                }
            }
            get("/admin") { call.respondText("static") }
        }

        val response = client.get("/admin")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static", response.bodyAsText())
    }

    @Test
    fun multipleAuthenticationProvidersDoNotOutrankUnauthenticatedStaticRoute() = testApplication {
        installAlwaysFailingBasicAuth("providerA", "providerB")

        routing {
            authenticate("providerA") {
                get("/{x}") { call.respondText("paramA") }
            }
            authenticate("providerB") {
                get("/{x}") { call.respondText("paramB") }
            }
            get("/settings") { call.respondText("static") }
        }

        val response = client.get("/settings")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static", response.bodyAsText())
    }

    @Test
    fun regexRouteWinsOverAuthenticatedParameterRoute() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            // Regex path segments share the same (highest) quality bucket as constant segments.
            get(Regex("""^\d+$""")) { call.respondText("regex") }
            authenticate {
                get("/{id}") { call.respondText("param") }
            }
        }

        val response = client.get("/123")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("regex", response.bodyAsText())
    }

    @Test
    fun authenticationIsStillEnforcedWhenTheAuthenticatedRouteIsTheOnlyMatch() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            authenticate {
                get("/report") { call.respondText("static") }
            }
            get("/other") { call.respondText("unrelated") }
        }

        // No unauthenticated route matches "/report" at all, so the authenticated one is
        // correctly selected and correctly rejected. This guards against a fix that makes
        // routing quality resolution *ignore* authenticated routes altogether instead of merely
        // deprioritizing them relative to strictly-better unauthenticated matches.
        val response = client.get("/report")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /**
     * Regression test for a second, subtler manifestation of the same class of bug as KTOR-613.
     *
     * `/report/{id?}` and `authenticate { get("/report") }` are on separate routing-tree branches
     * (the `authenticate` wrapper creates its own child node), so they never merge into a single
     * node the way two plain `get("/report")` / `get("/report/{id?}")` registrations would. When
     * resolving a request to "/report", both branches produce a full match:
     *  - the optional-parameter branch matches "report" (quality 1.0) then the missing `{id?}`
     *    (quality 0.2, the lowest defined quality bucket),
     *  - the authenticated branch matches only "report" (quality 1.0).
     *
     * Both branches tie on the one directly comparable entry (1.0 == 1.0), so resolution falls
     * back to a tie-break. A route with an extra, low-quality "missing optional parameter" entry
     * must never be preferred over a route that is a clean, exact, higher-quality match just
     * because it has one more entry in its match chain.
     */
    @Test
    fun authenticatedExactRouteWinsOverUnauthenticatedRouteWithMissingOptionalParameter() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            get("/report/{id?}") { call.respondText("optional") }
            authenticate {
                get("/report") { call.respondText("static") }
            }
        }

        val response = client.get("/report")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun authenticatedExactRouteWinsOverUnauthenticatedRouteWithMissingOptionalParameter_reverseOrder() =
        testApplication {
            installAlwaysFailingBasicAuth()

            routing {
                authenticate {
                    get("/report") { call.respondText("static") }
                }
                get("/report/{id?}") { call.respondText("optional") }
            }

            val response = client.get("/report")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // Note: the same defect can, in principle, be triggered without any `authenticate {}`
    // involved at all - `authenticate` is simply a common, well-defined way of putting two
    // routes that match the same URL on separate routing-tree branches (it always creates its
    // own child node), which is what actually triggers the flawed tie-break in
    // isBetterResolve. Two plain `get("/report")` / `get("/report/{id?}")` registrations don't
    // reproduce it because they merge into a single node (see RoutingNode.createChild), which
    // sidesteps the tie-break entirely via the existing per-node quality pruning.

    @Test
    fun unauthenticatedStaticRouteWinsOverAuthenticatedTailcardRoute() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            get("/files/readme") { call.respondText("static") }
            authenticate {
                get("/files/{...}") { call.respondText("tailcard") }
            }
        }

        val response = client.get("/files/readme")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static", response.bodyAsText())
    }

    @Test
    fun deeplyNestedUnauthenticatedStaticRouteWinsOverAuthenticatedParameterBranch() = testApplication {
        installAlwaysFailingBasicAuth()

        routing {
            authenticate {
                route("/api") {
                    route("/{version}") {
                        get("/users") { call.respondText("param-branch") }
                    }
                }
            }
            route("/api") {
                route("/v1") {
                    get("/users") { call.respondText("static-branch") }
                }
            }
        }

        val response = client.get("/api/v1/users")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("static-branch", response.bodyAsText())
    }

    @Test
    fun equalQualityRoutesPreserveFirstRegisteredWinsBehavior() = testApplication {
        routing {
            // Two distinct sibling nodes (different parameter names) with identical quality
            // (qualityParameter for both). Neither is authenticated, so this isolates the
            // pre-existing, unrelated tie-break behavior: the first-registered sibling wins.
            get("/{a}") { call.respondText("first") }
            get("/{b}") { call.respondText("second") }
        }

        val response = client.get("/x")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("first", response.bodyAsText())
    }
}
