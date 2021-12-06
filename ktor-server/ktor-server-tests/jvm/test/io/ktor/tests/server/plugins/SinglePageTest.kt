/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.plugins.*
import io.ktor.server.testing.*
import kotlin.test.*

class SinglePageTest {
    @Test
    fun testPageGet() = testApplication {
        install(SinglePage) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "CORSTest.kt"
        }

        client.get("/selected/StatusPageTest.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/selected").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }
    }

    @Test
    fun testIgnoreRoutes() = testApplication {
        install(SinglePage) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "CORSTest.kt"
            ignoredFiles = listOf(Regex("CallIdTest\\.kt"), Regex("Part."))
        }

        client.get("/StatusPageTest.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        assertFailsWith<ClientRequestException> {
            client.get("/CallIdTest.kt")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/PartialContentTest.kt")
        }
    }

    @Test
    fun testIgnoreAllRoutes() = testApplication {
        install(SinglePage) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            defaultPage = "CORSTest.kt"
            ignoredFiles = listOf(Regex("."))
        }
        assertFailsWith<ClientRequestException> {
            client.get("/CallIdTest.kt")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/")
        }
    }

    @Test
    fun testResources() = testApplication {
        install(SinglePage) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "CORSTest.class"
        }

        client.get("/StaticContentTest.class").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }
    }

    @Test
    fun testIgnoreResourceRoutes() = testApplication {
        install(SinglePage) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "CORSTest.class"
            ignoredFiles = listOf(Regex("CallIdTest\\.class"), Regex("Part."))
        }

        client.get("/StatusPageTest.class").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
        }

        assertFailsWith<ClientRequestException> {
            client.get("/CallIdTest.class").let {
                println(it.status)
            }
            println("Hello")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/PartialContentTest.class")
        }
    }

    @Test
    fun testIgnoreAllResourceRoutes() = testApplication {
        install(SinglePage) {
            useResources = true
            filesPath = "io.ktor.tests.server.plugins"
            defaultPage = "CORSTest.class"
            ignoredFiles = listOf(Regex("."))
        }
        assertFailsWith<ClientRequestException> {
            client.get("/CallIdTest.class")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/")
        }
    }
}
