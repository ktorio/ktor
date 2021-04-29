/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.content.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

class BodyProgressTest : ClientLoader() {

    @Serializable
    data class User(val login: String, val id: Long)

    private var invokedCount: Long by shared(0)

    @Test
    fun testSendDataClass() = clientTests {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                contentType(ContentType.Application.Json)
                body = User("123".repeat(5000), 1)
                onUpload(listener)
            }
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testSendChannel() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { count, total -> invokedCount++ }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close()
            }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                body = channel
                onUpload(listener)
            }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testSendByteArray() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { count, total -> invokedCount++ }

            val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                body = ByteArray(1025 * 16)
                onUpload(listener)
            }
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
                val response: HttpResponse = client.post("$TEST_SERVER/content/echo") {
                    body = channel
                    onUpload(listener)
                }
            }
        }
    }

    @Test
    fun testReceiveDataClassWithExecute() = clientTests {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            client.prepareGet("$TEST_SERVER/json/users-long") {
                contentType(ContentType.Application.Json)
                onDownload(listener)
            }.execute { it.body<List<User>>() }
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testReceiveDataClassWithReceive() = clientTests {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { sent, _ -> invokedCount++ }

            client.prepareGet("$TEST_SERVER/json/users-long") {
                contentType(ContentType.Application.Json)
                onDownload(listener)
            }.body<List<User>>()
            assertTrue(invokedCount >= 2)
        }
    }

    @Test
    fun testReceiveChannelWithExecute() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { count, total -> invokedCount++ }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close()
            }

            client.preparePost("$TEST_SERVER/content/echo") {
                body = channel
                onDownload(listener)
            }.execute { it.body<ByteReadChannel>().readRemaining() }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveChannelWithReceive() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { count, total -> invokedCount++ }

            val channel = ByteChannel()
            GlobalScope.launch {
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.writeFully(ByteArray(8 * 1025) { 1 })
                channel.close()
            }

            client.preparePost("$TEST_SERVER/content/echo") {
                body = channel
                onDownload(listener)
            }.body<ByteReadChannel, Unit> { it.readRemaining() }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveByteArrayWithExecute() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { count, total -> invokedCount++ }

            client.preparePost("$TEST_SERVER/content/echo") {
                body = ByteArray(1025 * 16)
                onDownload(listener)
            }.execute { it.body<ByteArray>() }
            assertTrue(invokedCount > 2)
        }
    }

    @Test
    fun testReceiveByteArrayWithReceive() = clientTests {
        test { client ->
            invokedCount = 0
            val listener: ProgressListener = { _, _ -> invokedCount++ }

            client.preparePost("$TEST_SERVER/content/echo") {
                body = ByteArray(1025 * 16)
                onDownload(listener)
            }.body<ByteArray>()

            assertTrue(invokedCount > 2)
        }
    }
}
