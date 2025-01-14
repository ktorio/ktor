/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlin.test.Test
import kotlin.test.assertEquals

class ByteWriteChannelSinkTest {

    @Test
    fun `write byte`() = runTest {
        val result = CompletableDeferred<Byte>()
        val sink = reader {
            result.complete(channel.readByte())
        }.channel.asSink().buffered()

        sink.writeByte(42)
        sink.flush()

        assertEquals(42, result.await())
    }
}
