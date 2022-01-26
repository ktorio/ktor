/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.plugins.*
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

        client.get("/selected/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/selected").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }
    }

    @Test
    fun testIgnoreRoutes() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "SinglePageApplicationTest.kt"
            ignoreFiles { it.contains("Empty1.kt") }
            ignoreFiles { it.endsWith("Empty2.kt") }
        }

        client.get("/Empty3.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        assertFailsWith<ClientRequestException> {
            client.get("/Empty1.kt")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/Empty2.kt")
        }
    }

    @Test
    fun testIgnoreAllRoutes() = testApplication {
        install(SinglePageApplication) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "SinglePageApplicationTest.kt"
            ignoreFiles { true }
        }
        assertFailsWith<ClientRequestException> {
            client.get("/Empty1.kt")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/")
        }
    }

    @Test
    fun testResources() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.class"
        }

        client.get("/Empty1.class").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }
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

        client.get("/Empty3.class").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        assertFailsWith<ClientRequestException> {
            client.get("/Empty1.class")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/Empty2.class")
        }
    }

    @Test
    fun testIgnoreAllResourceRoutes() = testApplication {
        install(SinglePageApplication) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "SinglePageApplicationTest.kt"
            ignoreFiles { true }
        }
        assertFailsWith<ClientRequestException> {
            client.get("/Empty1.class")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/")
        }
    }

    @Test
    fun testShortcut() = testApplication {
        install(SinglePageApplication) {
            angular("jvm/test/io/ktor/tests/server/plugins")
        }

        client.get("/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }
    }
}
