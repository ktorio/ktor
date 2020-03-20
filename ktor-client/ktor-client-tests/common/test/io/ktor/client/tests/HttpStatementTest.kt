/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.features.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class HttpStatementTest : ClientLoader() {

    @Test
    fun testExecute() = clientTests(listOf("curl")) {
        test { client ->
            client.request<HttpStatement>("$TEST_SERVER/content/stream").execute {
                val expected = buildPacket {
                    repeat(42) {
                        writeInt(42)
                    }
                }.readBytes(42)

                val actual = it.readBytes(42)

                assertArrayEquals("Invalid content", expected, actual)
            }

            val response = client.request<HttpStatement>("$TEST_SERVER/content/hello").execute()
            assertEquals("hello", response.receive<String>())
        }
    }

    @Test
    fun testGZipFromSavedResponse() = clientTests {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get<HttpResponse>("$TEST_SERVER/compression/gzip")
            assertTrue(!response.coroutineContext[Job]!!.isCompleted)

            val content = response.receive<String>()
            assertEquals("Compressed response!", content)
            assertTrue(response.coroutineContext[Job]!!.isCompleted)
        }
    }
}
