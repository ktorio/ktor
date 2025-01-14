/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MockUtilsTest {

    @Test
    fun testWriteChannelContentToByteArray() = runTest {
        val content = object : OutgoingContent.WriteChannelContent() {
            override suspend fun writeTo(channel: ByteWriteChannel) {
                channel.writeFully(ByteArray(8 * 1024))
            }
        }.toByteArray()

        assertEquals(8 * 1024, content.size)
    }
}
