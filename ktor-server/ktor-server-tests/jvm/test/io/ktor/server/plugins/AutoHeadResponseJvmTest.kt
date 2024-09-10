/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class AutoHeadResponseJvmTest {

    @Test
    fun testTextRespond() = testApplication {
        install(AutoHeadResponse)

        routing {
            get("/") {
                call.respondTextWriter {
                    write("Hello")
                }
            }
        }

        client.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello", response.bodyAsText())
        }

        client.head("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().isEmpty())
        }
    }
}
