/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val DOUBLE_TEST_ARRAY = ByteArray(16 * 1025) { 1 }
private val TEST_ARRAY = ByteArray(8 * 1025) { 1 }
private val TEST_NAME = "123".repeat(5000)

@OptIn(DelicateCoroutinesApi::class)
class BodyProgressTest : ClientLoader() {

    @Serializable
    data class User(val login: String, val id: Long)

    private var invokedCount: Long = 0

    @Test
    fun testSendDataClass() = clientTests {
        config {
            install(ContentNegotiation) {
                json()
            }
        }

        test { client ->
            invokedCount = 0

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                contentType(ContentType.Application.Json)
                setBody(User(TEST_NAME, 1))
                onUpload { _, _ -> invokedCount++ }
            }
            assertEquals("""{"login":"$TEST_NAME","id":1}""", response.body())
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testSendWriteChannelContent() = clientTests {
        test { client ->
            invokedCount = 0

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                setBody(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentType = ContentType.Application.OctetStream
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(TEST_ARRAY)
                            channel.writeFully(TEST_ARRAY)
                            channel.close()
                        }
                    }
                )
                onUpload { _, _ -> invokedCount++ }
            }
            assertContentEquals(DOUBLE_TEST_ARRAY, response.body())
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testSendChannel() = clientTests {
        test { client ->
            invokedCount = 0

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(TEST_ARRAY)
                channel.writeFully(TEST_ARRAY)
                channel.close()
            }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                setBody(channel)
                onUpload { _, _ -> invokedCount++ }
            }
            assertContentEquals(DOUBLE_TEST_ARRAY, response.body())
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testSendByteArray() = clientTests {
        test { client ->
            invokedCount = 0

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                setBody(DOUBLE_TEST_ARRAY)
                onUpload { _, _ -> invokedCount++ }
            }
            assertContentEquals(DOUBLE_TEST_ARRAY, response.body())
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveDataClassWithExecute() = clientTests {
        config {
            install(ContentNegotiation) {
                json()
            }
        }

        test { client ->
            invokedCount = 0

            client.prepareGet("$TEST_SERVER/json/users-long") {
                contentType(ContentType.Application.Json)
                onDownload { _, _ -> invokedCount++ }
            }.execute {
                val result = it.body<List<User>>()
                val users = buildList { repeat(300) { add(User(id = it.toLong(), login = "TestLogin-$it")) } }
                assertEquals(users, result)
            }
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testReceiveDataClassWithReceive() = clientTests {
        config {
            install(ContentNegotiation) {
                json()
            }
        }

        test { client ->
            invokedCount = 0

            client.prepareGet("$TEST_SERVER/json/users-long") {
                contentType(ContentType.Application.Json)
                onDownload { _, _ -> invokedCount++ }
            }.body<List<User>, Unit> {
                val users = buildList { repeat(300) { add(User(id = it.toLong(), login = "TestLogin-$it")) } }
                assertEquals(users, it)
            }
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testReceiveChannelWithExecute() = clientTests {
        test { client ->
            invokedCount = 0

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(TEST_ARRAY)
                channel.writeFully(TEST_ARRAY)
                channel.close()
            }

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(channel)
                onDownload { _, _ -> invokedCount++ }
            }.execute {
                val result = it.body<ByteReadChannel>().readRemaining().readBytes()
                assertContentEquals(DOUBLE_TEST_ARRAY, result)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveChannelWithReceive() = clientTests {
        test { client ->
            invokedCount = 0

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(TEST_ARRAY)
                channel.writeFully(TEST_ARRAY)
                channel.close()
            }

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(channel)
                onDownload { _, _ -> invokedCount++ }
            }.body<ByteReadChannel, Unit> {
                val result = it.readRemaining().readBytes()
                assertContentEquals(DOUBLE_TEST_ARRAY, result)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveByteArrayWithExecute() = clientTests {
        test { client ->
            invokedCount = 0

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(DOUBLE_TEST_ARRAY)
                onDownload { _, _ -> invokedCount++ }
            }.execute {
                val result = it.body<ByteArray>()
                assertContentEquals(DOUBLE_TEST_ARRAY, result)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveByteArrayWithReceive() = clientTests {
        test { client ->
            invokedCount = 0

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(DOUBLE_TEST_ARRAY)
                onDownload { _, _ -> invokedCount++ }
            }.body<ByteArray, Unit> {
                assertContentEquals(DOUBLE_TEST_ARRAY, it)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testWithCache() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    respond(DOUBLE_TEST_ARRAY)
                }
            }
            install(HttpCache)
        }
        test { client ->
            val response = client.get("$TEST_SERVER/content/echo") {
                onDownload { _, _ -> }
            }.body<ByteArray>()
            assertContentEquals(DOUBLE_TEST_ARRAY, response)
        }
    }

    @Test
    fun testContentLengthNotNegative() = clientTests {
        test { client ->
            client.get("$TEST_SERVER/compression/gzip-large") {
                onDownload { _, length -> assertTrue(length == null || length > 0) }
            }
        }
    }
}
