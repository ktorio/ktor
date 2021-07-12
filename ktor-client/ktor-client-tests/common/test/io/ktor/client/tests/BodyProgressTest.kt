/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.content.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.json.*
import io.ktor.client.plugins.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

@OptIn(DelicateCoroutinesApi::class)
class BodyProgressTest : ClientLoader() {

    @Serializable
    data class User(val login: String, val id: Long)

    private var invokedCount: Long by shared(0)

    @Test
    fun testSendDataClass() = clientTests {
        config {
            install(JsonPlugin) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                contentType(ContentType.Application.Json)
                setBody(User("123".repeat(5000), 1))
                onUpload(listener)
            }
            assertEquals("""{"login":"${"123".repeat(5000)}","id":1}""", response.body())
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testSendWriteChannelContent() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                setBody(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentType = ContentType.Application.OctetStream
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(ByteArray(8 * 1025) { 1 })
                            channel.writeFully(ByteArray(8 * 1025) { 1 })
                            channel.close()
                        }
                    }
                )
                onUpload(listener)
            }
            assertContentEquals(ByteArray(16 * 1025) { 1 }, response.body())
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testSendChannel() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close()
            }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                setBody(channel)
                onUpload(listener)
            }
            assertContentEquals(ByteArray(16 * 1025) { 1 }, response.body())
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testSendByteArray() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                setBody(ByteArray(1025 * 16) { 1 })
                onUpload(listener)
            }
            assertContentEquals(ByteArray(16 * 1025) { 1 }, response.body())
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testSendFailedChannel() = clientTests {
        test { client ->
            val listener: ProgressListener = { _, _ -> }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close(RuntimeException("Error"))
            }

            assertFailsWith<RuntimeException> {
                client.post("$TEST_SERVER/content/echo") {
                    setBody(channel)
                    onUpload(listener)
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testReceiveDataClassWithExecute() = clientTests {
        config {
            install(JsonPlugin) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            client.prepareGet("$TEST_SERVER/json/users-long") {
                contentType(ContentType.Application.Json)
                onDownload(listener)
            }.execute {
                val result = it.body<List<User>>()
                val users = buildList { repeat(300) { add(User(id = it.toLong(), login = "TestLogin-$it")) } }
                assertEquals(users, result)
            }
            assertTrue(invokedCount >= 2)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testReceiveDataClassWithReceive() = clientTests {
        config {
            install(JsonPlugin) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            client.prepareGet("$TEST_SERVER/json/users-long") {
                contentType(ContentType.Application.Json)
                onDownload(listener)
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
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close()
            }

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(channel)
                onDownload(listener)
            }.execute {
                val result = it.body<ByteReadChannel>().readRemaining().readBytes()
                assertContentEquals(ByteArray(16 * 1025) { 1 }, result)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveChannelWithReceive() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close()
            }

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(channel)
                onDownload(listener)
            }.body<ByteReadChannel, Unit> {
                val result = it.readRemaining().readBytes()
                assertContentEquals(ByteArray(16 * 1025) { 1 }, result)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveByteArrayWithExecute() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(ByteArray(1025 * 16) { 1 })
                onDownload(listener)
            }.execute {
                val result = it.body<ByteArray>()
                assertContentEquals(ByteArray(16 * 1025) { 1 }, result)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveByteArrayWithReceive() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            client.preparePost("$TEST_SERVER/content/echo") {
                setBody(ByteArray(1025 * 16) { 1 })
                onDownload(listener)
            }.body<ByteArray, Unit> {
                assertContentEquals(ByteArray(16 * 1025) { 1 }, it)
            }
            assertTrue(invokedCount > 2)
        }
    }
}
