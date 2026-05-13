/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpClientConfigTest {

    @Test
    fun testPluginInstalledTwice() {
        var configuration = 0
        var installation = 0
        var first = 0
        var second = 0

        class Config {
            init {
                configuration++
            }
        }

        val plugin = createClientPlugin("hey", ::Config) {
            installation++
        }

        HttpClient {
            install(plugin) {
                first += 1
            }

            install(plugin) {
                second += 1
            }
        }

        assertEquals(1, configuration)
        assertEquals(1, installation)
        assertEquals(1, first)
        assertEquals(1, second)
    }

    @Test
    fun closingChildDoesNotCloseEngine() = runTest {
        var engineClosed = false
        val client = HttpClient(createTestEngineFactory { engineClosed = true })
        val child = client.config {}

        assertSame(client.engine, child.engine)

        child.close()

        assertFalse(engineClosed)
        client.close()
        assertTrue(engineClosed)
    }

    @Test
    fun closingParentDoesNotCloseEngineWhileChildIsOpen() = runTest {
        var engineClosed = false
        val client = HttpClient(createTestEngineFactory { engineClosed = true })
        val child = client.config {}

        assertSame(client.engine, child.engine)

        client.close()

        assertFalse(engineClosed)
        child.close()
        assertTrue(engineClosed)
    }

    @Test
    fun cancellingChildDoesNotCancelEngine() = runTest {
        val client = HttpClient(createTestEngineFactory {})
        val child = client.config {}

        assertSame(client.engine, child.engine)

        child.coroutineContext[Job]!!.cancel()

        assertFalse(client.engine.coroutineContext[Job]!!.isCancelled)
        client.coroutineContext[Job]!!.cancel()
        assertTrue(client.engine.coroutineContext[Job]!!.isCancelled)
    }

    @Test
    fun cancellingParentDoesNotCancelEngineWhileChildIsOpen() = runTest {
        val client = HttpClient(createTestEngineFactory {})
        val child = client.config {}

        assertSame(client.engine, child.engine)

        client.coroutineContext[Job]!!.cancel()

        assertFalse(child.engine.coroutineContext[Job]!!.isCancelled)
        child.coroutineContext[Job]!!.cancel()
        assertTrue(child.engine.coroutineContext[Job]!!.isCancelled)
    }

    private fun createTestEngineFactory(onClose: () -> Unit): HttpClientEngineFactory<HttpClientEngineConfig> {
        val engine = object : HttpClientEngineBase("test-engine") {
            override val config: HttpClientEngineConfig = HttpClientEngineConfig()

            @InternalAPI
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                return HttpResponseData(
                    HttpStatusCode.OK,
                    GMTDate.START,
                    Headers.Empty,
                    HttpProtocolVersion.HTTP_1_1,
                    Unit,
                    EmptyCoroutineContext
                )
            }

            override fun close() {
                super.close()
                onClose()
            }
        }
        return object : HttpClientEngineFactory<HttpClientEngineConfig> {
            override fun create(block: HttpClientEngineConfig.() -> Unit) = engine
        }
    }
}
