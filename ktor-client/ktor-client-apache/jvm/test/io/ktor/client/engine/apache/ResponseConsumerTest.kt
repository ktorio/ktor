/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.apache

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.apache.http.nio.*
import java.nio.*
import kotlin.test.*

class ResponseConsumerTest {
    private val parentContext = Dispatchers.Main + Job()

    @Test
    fun testConsumeContent() {
        val body = object : OutgoingContent.WriteChannelContent() {
            override suspend fun writeTo(channel: ByteWriteChannel) {
                channel.writeFully(ByteArray(4088))
            }
        }

        val requestData = HttpRequestData(
            URLBuilder().build(),
            HttpMethod.Get,
            Headers.Empty,
            body,
            Job(),
            Attributes()
        )
        val consumer = ApacheResponseConsumer(parentContext, requestData)

        val decoder = object : ContentDecoder {
            override fun read(dst: ByteBuffer): Int {
                val result = dst.remaining()
                dst.position(dst.limit())
                return result
            }

            override fun isCompleted(): Boolean = false
        }

        consumer.consumeContent(decoder, NoOpControl())

        // Shouldn't freeze.
        assertFails {
            consumer.consumeContent(decoder, NoOpControl())
        }
    }
}
