/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.plugins.spa.*
import io.ktor.server.testing.*
import kotlin.test.*

class SinglePageApplicationTest {
    @Test
    fun fullWithFilesTest() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "Empty3.kt"
            ignoreFiles { it.contains("Empty2.kt") }
        }

        client.get("/selected").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/selected/a").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/selected/Empty2.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/selected/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty1)
        }
    }

    @Test
    fun testPageGet() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "Empty3.kt"
        }

        client.get("/selected/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty1)
        }

        client.get("/selected").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }
    }

    @Test
    fun testIgnoreAllRoutes() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "Empty3.kt"
            ignoreFiles { true }
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/a").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }
    }

    @Test
    fun testResources() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.class"
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/SinglePageApplicationTest.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/a").status)
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
        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/Empty2.class").status)
    }

    @Test
    fun testIgnoreAllResourceRoutes() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.class"
            ignoreFiles { true }
        }

        assertEquals(HttpStatusCode.OK, client.get("/SinglePageApplicationTest.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/a").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun testShortcut() = testApplication {
        install(SinglePageApplication) {
            angular("jvm/test/io/ktor/tests/server/plugins")
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty1.kt").status)
    }

    private val empty1 = """
        package io.ktor.tests.server.plugins/*
         * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
         */

        // required for tests
        class Empty1
    """.trimIndent()

    private val empty3 = """
        package io.ktor.tests.server.plugins/*
         * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
         */

        // required for tests
        class Empty3
    """.trimIndent()
}
