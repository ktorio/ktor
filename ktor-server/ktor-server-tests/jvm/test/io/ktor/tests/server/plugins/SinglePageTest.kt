/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.plugins.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class SinglePageTest {
    @Test
    fun testPageGet() = testApplication {
        install(SinglePage) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "CORSTest.kt"
        }

        var response = client.get("/selected/StatusPageTest.kt")
        assertEquals(response.status, HttpStatusCode.OK)

        response = client.get("/selected")
        assertEquals(response.status, HttpStatusCode.OK)
    }

    @Test
    fun testIgnoreRoutes() = testApplication {
        install(SinglePage) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "CORSTest.kt"
            ignoredRoutes = listOf(Regex("CallIdTest\\.kt"), Regex("Part."))
        }

        var response = client.get("/selected/StatusPageTest.kt")
        assertEquals(response.status, HttpStatusCode.OK)

        response = client.get("/selected")
        assertEquals(response.status, HttpStatusCode.OK)

        assertFailsWith<ClientRequestException> {
            client.get("/selected/CallIdTest.kt")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/selected/PartialContentTest.kt")
        }
    }

    @Test
    fun testIgnoreAllRoutes() = testApplication {
        install(SinglePage) {
            filesPath = "jvm/test/io/ktor/tests/server/plugins"
            applicationRoute = "selected"
            defaultPage = "CORSTest.kt"
            ignoredRoutes = listOf(Regex("."))
        }
        assertFailsWith<ClientRequestException> {
            client.get("/selected/CallIdTest.kt")
        }

        assertFailsWith<ClientRequestException> {
            client.get("/selected")
        }
    }
}
