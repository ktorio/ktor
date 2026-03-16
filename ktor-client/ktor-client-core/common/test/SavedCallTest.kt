/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SavedCallTest {

    @OptIn(InternalAPI::class)
    @Test
    fun `saved response bodyAsByteArray returns cached bytes`() = runTest {
        val expectedBody = "hello world"
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond(expectedBody, HttpStatusCode.OK) }
            }
        }

        val savedCall = client.get("http://localhost/test").call.save()
        val response = savedCall.response

        // bodyAsByteArray should return the cached bytes, ignoring the channel argument
        val unrelatedChannel = ByteReadChannel("different content")
        val result = response.bodyAsByteArray(unrelatedChannel)

        assertContentEquals(expectedBody.encodeToByteArray(), result)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `default response bodyAsByteArray reads from channel`() = runTest {
        val expectedBody = "channel content"
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond(expectedBody, HttpStatusCode.OK) }
            }
        }

        val response = client.get("http://localhost/test")

        // Default bodyAsByteArray should read from the provided channel
        val channel = ByteReadChannel(expectedBody.encodeToByteArray())
        val result = response.bodyAsByteArray(channel)

        assertContentEquals(expectedBody.encodeToByteArray(), result)
    }

    @Test
    fun `mutating returned byte array does not affect subsequent reads`() = runTest {
        val expectedBody = "immutable body"
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond(expectedBody, HttpStatusCode.OK) }
            }
        }

        val savedCall = client.get("http://localhost/test").call.save()

        val firstRead = savedCall.body<ByteArray>()
        // Mutate every byte in the returned array
        for (i in firstRead.indices) {
            firstRead[i] = 0
        }

        val secondRead = savedCall.body<ByteArray>()
        assertContentEquals(expectedBody.encodeToByteArray(), secondRead)
    }

    @Test
    fun `saved call allows double receive`() = runTest {
        val expectedBody = "test body"
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond(expectedBody, HttpStatusCode.OK) }
            }
        }

        val savedCall = client.get("http://localhost/test").call.save()

        // Should be able to read the body multiple times from a saved call
        val first = savedCall.body<ByteArray>()
        val second = savedCall.body<ByteArray>()

        assertContentEquals(expectedBody.encodeToByteArray(), first)
        assertContentEquals(expectedBody.encodeToByteArray(), second)
    }
}
