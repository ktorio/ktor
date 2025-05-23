/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ClientConfigurationTest {

    @Test
    fun testClientConfiguration() = testApplication {
        // Set up a simple route
        application {
            routing {
                get("/hello") {
                    call.respondText("Hello, World!")
                }
            }
        }

        // Store the original client
        val originalClient = client

        // Create a new client
        val configuredClient = createClient { }

        // Set the new client as the default
        client = configuredClient

        // Verify that the client has been changed
        assertNotSame(originalClient, client)

        // Test the configured client
        val response = client.get("/hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, World!", response.bodyAsText())
    }
}
