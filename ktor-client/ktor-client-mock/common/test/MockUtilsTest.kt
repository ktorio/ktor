package io.ktor.client.engine.mock

import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlin.test.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class MockUtilsTest {

    @Test
    fun testWriteChannelContentToByteArray() = testSuspend {
        val content = object : OutgoingContent.WriteChannelContent() {
            override suspend fun writeTo(channel: ByteWriteChannel) {
                channel.writeFully(ByteArray(8 * 1024))
            }
        }.toByteArray()

        assertEquals(8 * 1024, content.size)
    }
}
