/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.apikey

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMinimalExampleApp {
    @Test
    fun `test minimal example app works as expected`() {
        testApplication {
            application(Application::minimalExample)

            val unauthorizedResponse = client.get("/")
            assertEquals(HttpStatusCode.Unauthorized, unauthorizedResponse.status)

            val response = client.get("/") {
                header("X-Api-Key", "this-is-expected-key")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Key: this-is-expected-key", response.bodyAsText())
        }
    }
}
