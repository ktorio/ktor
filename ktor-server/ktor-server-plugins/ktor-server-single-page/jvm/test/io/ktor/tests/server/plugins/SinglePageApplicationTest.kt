/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.plugins.spa.*
import io.ktor.server.testing.*
import kotlin.test.*

class SinglePageApplicationTest {
    @Test
    fun testPageGet() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "Empty3.kt"
        }

        assertEquals(client.get("/selected/Empty1.kt").status, HttpStatusCode.OK)

        assertEquals(client.get("/selected").status, HttpStatusCode.OK)
    }

    @Test
    fun testIgnoreRoutes() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "SinglePageApplicationTest.kt"
            ignoreFiles { it.contains("Empty1.kt") }
            ignoreFiles { it.endsWith("Empty2.kt") }
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty3.kt").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty1.kt").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty2.kt").status)
    }

    @Test
    fun testIgnoreAllRoutes() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "SinglePageApplicationTest.kt"
            ignoreFiles { true }
        }

        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty1.kt").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty1.kt").status)
    }

    @Test
    fun testResources() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.class"
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun testIgnoreResourceRoutes() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.class"
            ignoreFiles { it.contains("Empty1.class") }
            ignoreFiles { it.endsWith("Empty2.class") }
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty3.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)

        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty2.class").status)
    }

    @Test
    fun testIgnoreAllResourceRoutes() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.kt"
            ignoreFiles { true }
        }

        assertEquals(HttpStatusCode.Forbidden, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/").status)
    }

    @Test
    fun testShortcut() = testApplication {
        install(SinglePageApplication) {
            angular("jvm/test/io/ktor/tests/server/plugins")
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty1.kt").status)
    }
}
