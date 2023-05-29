/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.tests.utils.*
import io.ktor.sse.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.test.*

internal val ENGINES_WITHOUT_SSE =
    listOf("Android", "Apache", "Apache5", "Curl", "Darwin", "DarwinLegacy", "CIO", "Java", "Js", "WinHttp", "Jetty")

class ServerSentEventsTest : ClientLoader() {

    @Test
    fun testExceptionIfSseIsNotInstalled() = testSuspend {
        val client = HttpClient()
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.serverSentEventsSession()
        }.let {
            assertContains(it.message!!, ServerSentEvents.key.name)
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.serverSentEvents {}
        }.let {
            assertContains(it.message!!, ServerSentEvents.key.name)
        }
    }

    @Test
    fun testSseSession() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(ServerSentEvents)
        }

        test { client ->
            val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello")
            session.incoming.receive().apply {
                assertEquals("0", id)
                assertEquals("hello 0", event)
                assertEquals("hello\nfrom server", data)
            }
            session.cancel()
        }
    }

    @Test
    fun testParallelSseSessions() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(ServerSentEvents)
        }

        test { client ->
            coroutineScope {
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=100")
                    for (i in 0 until 100) {
                        session.incoming.receive().apply {
                            assertEquals("$i", id)
                            assertEquals("hello $i", event)
                            assertEquals("hello\nfrom server", data)
                        }
                    }
                    session.cancel()
                }
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=50")
                    for (i in 0 until 50) {
                        session.incoming.receive().apply {
                            assertEquals("$i", id)
                            assertEquals("hello $i", event)
                            assertEquals("hello\nfrom server", data)
                        }
                    }
                    session.cancel()
                }
            }
        }
    }

    @Test
    fun testSseSessionWithError() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(ServerSentEvents)
        }

        test { client ->
            kotlin.test.assertFailsWith<ServerSentEventsException> {
                client.serverSentEventsSession("http://testerror.com/sse")
            }
        }
    }

    @Test
    fun testExceptionSse() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(ServerSentEvents)
        }

        test { client ->
            kotlin.test.assertFailsWith<ServerSentEventsException> {
                client.serverSentEvents("$TEST_SERVER/sse/hello") { error("error") }
            }.let {
                assertContains(it.message!!, "error")
            }
        }
    }
}
