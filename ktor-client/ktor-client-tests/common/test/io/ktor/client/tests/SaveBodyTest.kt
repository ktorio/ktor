/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.utils.io.*
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

    @Test
    fun `body is saved even if deprecated plugin is disabled`() = testClient {
        config {
            engine {
                addHandler {
                    respondOk("Test")
                }
            }

            @Suppress("DEPRECATION")
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
    fun `deprecated skipSavingBody doesn't prevent body from being saved`() = testClient {
        config {
            engine {
                addHandler {
                    respondOk("Test")
                }
            }
        }

        test { client ->
            val response = client.get {
                @Suppress("DEPRECATION")
                skipSavingBody()
            }
            assertEquals("Test", response.bodyAsText())
            assertEquals("Test", response.bodyAsText())
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `wrongly implemented plugin shouldn't affect the resulting response replayability`() = testClient {
        config {
            engine {
                addHandler {
                    respondOk("Test")
                }
            }

            val makeBodyNonReplayable = createClientPlugin("MakeBodyNonReplayable") {
                client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                    val content = response.rawContent
                    val newResponse = response.call.replaceResponse { content }.response
                    proceedWith(newResponse)
                }
            }
            install(makeBodyNonReplayable)
        }

        test { client ->
            val response = client.get("/")
            assertEquals("Test", response.bodyAsText())
            assertEquals("Test", response.bodyAsText())
        }
    }
}
