/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class RoutingPathTreeTest {

    @Test
    fun `regex route does not disable constant route lookup`() = testApplication {
        application {
            val root = routing {
                get("/health") {}
                route("/api") {
                    get(Regex("/.+")) {}
                }
            }

            val pathTree = RoutingPathTree.build(root)
            assertNotNull(pathTree.tryResolve("/health", HttpMethod.Get))
            assertNull(pathTree.tryResolve("/api/test", HttpMethod.Get))
        }
    }

    @Test
    fun `header route does not disable constant route lookup`() = testApplication {
        application {
            val root = routing {
                get("/health") {}
                route("/api") {
                    header("X-Test", "value") {
                        get("/private") {}
                    }
                }
            }

            val pathTree = RoutingPathTree.build(root)
            assertNotNull(pathTree.tryResolve("/health", HttpMethod.Get))
            assertNull(pathTree.tryResolve("/api/private", HttpMethod.Get))
        }
    }

    @Test
    fun `optional parameter route does not disable constant route lookup`() = testApplication {
        application {
            val root = routing {
                get("/health") {}
                route("/api") {
                    get("/{id?}") {}
                }
            }

            val pathTree = RoutingPathTree.build(root)
            assertNotNull(pathTree.tryResolve("/health", HttpMethod.Get))
            assertNull(pathTree.tryResolve("/api/test", HttpMethod.Get))
        }
    }
}
