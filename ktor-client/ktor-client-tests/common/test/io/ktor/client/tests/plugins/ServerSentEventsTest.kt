/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.sse.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

internal val ENGINES_WITHOUT_SSE =
    listOf("Android", "Curl", "Darwin", "DarwinLegacy", "Java", "Js", "WinHttp")

class ServerSentEventsTest : ClientLoader() {

    @Test
    fun testExceptionIfSseIsNotInstalled() = testSuspend {
        val client = HttpClient()
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.serverSentEventsSession()
        }.let {
            assertContains(it.message!!, SSE.key.name)
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.serverSentEvents {}
        }.let {
            assertContains(it.message!!, SSE.key.name)
        }
    }

    @Test
    fun testSseSession() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(SSE)
        }

        test { client ->
            val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello")
            session.incoming.single().apply {
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
            install(SSE)
        }

        test { client ->
            coroutineScope {
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=100")
                    var size = 0
                    session.incoming.collectIndexed { i, it ->
                        assertEquals("$i", it.id)
                        assertEquals("hello $i", it.event)
                        assertEquals("hello\nfrom server", it.data)
                        size++
                    }
                    assertEquals(100, size)
                    session.cancel()
                }
                launch {
                    val session = client.serverSentEventsSession("$TEST_SERVER/sse/hello?times=50")
                    var size = 0
                    session.incoming.collectIndexed { i, it ->
                        assertEquals("$i", it.id)
                        assertEquals("hello $i", it.event)
                        assertEquals("hello\nfrom server", it.data)
                        size++
                    }
                    assertEquals(50, size)
                    session.cancel()
                }
            }
        }
    }

    @Test
    fun testSseSessionWithError() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(SSE)
        }

        test { client ->
            kotlin.test.assertFailsWith<SSEException> {
                client.serverSentEventsSession("http://testerror.com/sse")
            }
        }
    }

    @Test
    fun testExceptionSse() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(SSE)
        }

        test { client ->
            kotlin.test.assertFailsWith<SSEException> {
                client.serverSentEvents("$TEST_SERVER/sse/hello") { error("error") }
            }.let {
                assertContains(it.message!!, "error")
            }
        }
    }

    @Test
    fun testNoCommentsByDefault() = clientTests(ENGINES_WITHOUT_SSE) {
        config {
            install(SSE)
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                assertEquals(50, incoming.count())
                incoming.collectIndexed { i, it ->
                    assertEquals(ServerSentEvent(data = "$i"), it)
                }
            }
        }
    }

    @Test
    fun testShowComments() = clientTests(ENGINES_WITHOUT_SSE + "OkHttp") {
        config {
            install(SSE) {
                showCommentEvents()
            }
        }

        test { client ->
            client.serverSentEvents("$TEST_SERVER/sse/comments?times=50") {
                assertEquals(100, incoming.count())
                incoming.collectIndexed { i, it ->
                    if (i % 2 == 0) {
                        assertEquals(ServerSentEvent(data = "$i"), it)
                    } else {
                        assertEquals(ServerSentEvent(comments = "$i"), it)
                    }
                }
            }
        }
    }

    @Test
    fun testEndConditions() = clientTests(ENGINES_WITHOUT_SSE + "OkHttp") {
        config {
            install(SSE) {
                closeOn { it.contains("end") }
            }
        }

        val input = """
            data: 1
            
            data: 2 & end
            
            data: 3
        """.trimIndent() + "\n\n"

        test { client ->
            client.sse({
                url("$TEST_SERVER/sse/echo")
                parameter("input", input)
            }) {
                incoming.single().apply {
                    assertEquals(ServerSentEvent(data = "1").toString(), this.toString())
                }
            }
        }
    }
}
