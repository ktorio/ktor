/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.asSource
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ByteReadChannelSourceTest {

    @Test
    fun `read from closed fails`() {
        val channel = ByteReadChannel(ByteArray(0))

        assertFailsWith<IOException> {
            channel.asSource().buffered().readByte()
        }
    }

    @Test
    fun `read byte`() {
        val channel = ByteReadChannel(ByteArray(1) { 42 })

        val source = channel.asSource().buffered()
        val byte = source.readByte()

        assertEquals(42, byte)
    }

    @Test
    fun `resumes after write`() = runTest {
        val start = Job()
        val source = writer {
            start.join()
            channel.writeByte(42)
        }.channel.asSource()

        val context = newCoroutineContext(Dispatchers.Default)
        val result = CompletableDeferred<Byte>()
        launch(context) {
            result.complete(source.buffered().readByte())
        }

        start.complete()
        assertEquals(42, result.await())
    }

    @Test
    fun `cancel is propagating`() = runTest {
        var cause = CompletableDeferred<Throwable>()
        val channel = writer {
            try {
                while (true) {
                    channel.writeByte(42)
                }
            } catch (e: Throwable) {
                cause.complete(e)
            }
        }.channel

        channel.asSource().close()
        assertTrue(cause.await() is IOException)
    }
}
