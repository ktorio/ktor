/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaveBodyTest : ClientEngineTest<MockEngineConfig>(MockEngine) {

    @Test
    fun `body is saved for non-streaming response by default`() = testClient {
        config {
            engine {
                addHandler {
                    respondOk("Test")
                }
            }
        }

        test { client ->
            val response = client.get("/")
            assertEquals("Test", response.bodyAsText())
            assertEquals("Test", response.bodyAsText())
        }
    }

    @Test
    fun `body is saved for non-streaming response even if deprecated plugin is disabled`() = testClient {
        config {
            engine {
                addHandler {
                    respondOk("Test")
                }
            }

            install(SaveBodyPlugin) {
                disabled = true
            }
        }

        test { client ->
            val response = client.get("/")
            assertEquals("Test", response.bodyAsText())
            assertEquals("Test", response.bodyAsText())
        }
    }

    @Test
    fun `body is not saved for streaming response`() = testClient {
        config {
            engine {
                addHandler {
                    respondOk("Test")
                }
            }
        }

        test { client ->
            client.prepareGet("/").execute { response ->
                assertEquals("Test", response.bodyAsText())
                assertFailsWith<DoubleReceiveException> { response.bodyAsText() }
            }
        }
    }
}
