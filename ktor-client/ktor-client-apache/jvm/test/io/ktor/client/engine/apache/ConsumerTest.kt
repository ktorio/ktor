/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.debug.junit4.*
import org.apache.http.*
import org.apache.http.message.*
import org.apache.http.nio.*
import org.apache.http.protocol.*
import org.junit.*
import java.nio.*
import java.util.zip.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.test.Test

class ConsumerTest : CoroutineScope {
    private lateinit var thread: Thread
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + NonExistingDispatcher

    private lateinit var channel: ByteReadChannel
    private lateinit var receivedResponse: HttpResponse

    @get:Rule
    val timeout = CoroutinesTimeout(10000L, cancelOnTimeout = true)

    @BeforeTest
    fun setup() {
        thread = Thread.currentThread()
    }

    @AfterTest
    @UseExperimental(InternalCoroutinesApi::class)
    fun cancel() {
        if (job.isCancelled) {
            throw job.getCancellationException()
        }

        job.complete()
        runBlocking {
            withTimeout(1000L) {
                job.join()
            }
        }
        job.cancel()
    }

    @Test
    fun testCreating() {
        ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }.responseCompleted(BasicHttpContext())
    }

    @Test
    fun smokeTest() {
        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }

        val decoder = TestDecoder(byteArrayOf(1, 2, 3))
        decoder.barrier = 3

        consumer.responseReceived(response())
        consumer.consumeContent(decoder, AlwaysFailingIOControl())

        decoder.complete()
        consumer.consumeContent(decoder, AlwaysFailingIOControl())

        val found = runBlocking {
            channel.readRemaining().readBytes()
        }

        assertTrue(decoder.data.contentEquals(found))
    }

    @Test
    fun emptyContent() {
        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }

        val decoder = TestDecoder(byteArrayOf())
        decoder.complete()

        consumer.responseReceived(response())
        consumer.consumeContent(decoder, AlwaysFailingIOControl())

        val found = runBlocking {
            channel.readRemaining().readBytes()
        }

        assertTrue(decoder.data.contentEquals(found))
    }

    @Test
    fun emptyContentWhenItIsAlwaysEmpty() {
        // for some response kinds (HEAD, status NoContent as so on) consumeContent is not called
        // so we have completed immediately after response received

        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }

        val decoder = TestDecoder(byteArrayOf())
        decoder.complete()

        consumer.responseReceived(response())
        consumer.responseCompleted(BasicHttpContext())

        assertTrue { channel.isClosedForRead }
    }

    @Test
    fun consumeBeforeResponseReceived() {
        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }

        val decoder = TestDecoder(byteArrayOf(1, 2, 3))
        decoder.barrier = 1

        consumer.consumeContent(decoder, AlwaysFailingIOControl())
        consumer.responseReceived(response())

        (channel as ByteChannel).flush()
        assertEquals(1, channel.availableForRead)

        decoder.barrier = 3
        decoder.complete()
        consumer.consumeContent(decoder, AlwaysFailingIOControl())

        val found = runBlocking {
            channel.readRemaining().readBytes()
        }

        assertTrue(decoder.data.contentEquals(found))
    }

    @Test
    fun suspendSmokeTest() {
        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }

        val decoder = TestDecoder((1..8000).map { (it and 0xff).toByte() }.toByteArray())
        decoder.barrier = decoder.data.size

        val ioControl = SuspendableInputIOControl()

        consumer.responseReceived(response())
        consumer.consumeContent(decoder, ioControl)
        ioControl.assertSuspended()
        assertTrue { channel.availableForRead > 0 }
        assertEquals(decoder.data.size, channel.availableForRead + decoder.available)

        // we read from the channel so it should release free space

        val firstPart = runBlocking {
            channel.readPacket(channel.availableForRead)
        }

        // input should be resumed by the underlying logic: channel -> consumer -> ioControl
        ioControl.assertResumed()

        decoder.complete()
        consumer.consumeContent(decoder, ioControl)
        ioControl.assertResumed() // shouldn't suspend again

        val secondPart = runBlocking {
            channel.readPacket(channel.availableForRead)
        }

        val result = buildPacket {
            writePacket(firstPart)
            writePacket(secondPart)
        }.readBytes()

        assertTrue { result.contentEquals(decoder.data) }

        ioControl.assertResumed()
    }

    @Test
    fun integrationTest() {
        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }
        consumer.responseReceived(response())

        val consumerCrc = async(Dispatchers.Default) {
            val buffer = ByteBuffer.allocate(4096)
            val crc = CRC32()

            while (!channel.isClosedForRead) {
                buffer.clear()
                channel.readAvailable(buffer)
                buffer.flip()
                crc.update(buffer)
            }

            crc.value
        }

        // here we have test reactor imitation
        val ioControl = SuspendableInputIOControl()
        val decoder = ChannelDecoder()

        runBlocking {
            val producerCrc = async {
                try {
                    val crc = CRC32()

                    repeat(1000) {
                        repeat(16) { factor ->
                            val dataSize = 1 shl factor
                            val chunk = ByteArray(dataSize) { (it and 0xff).toByte() }
                            crc.update(chunk)
                            decoder.send(chunk)
                        }
                    }

                    crc.value
                } catch (cause: Throwable) {
                    decoder.failed(cause)
                    throw cause
                } finally {
                    decoder.close()
                }
            }

            while (!decoder.isCompleted && job.isActive) {
                yield()
                ioControl.waitNotSuspended()
                if (!ioControl.suspended) {
                    consumer.consumeContent(decoder, ioControl)
                }
            }

            if (!job.isActive) {
                producerCrc.cancel()
                consumerCrc.cancel()
                consumer.consumeContent(decoder, ioControl)
            } else {
                check(decoder.isCompleted) { "Decoder expected to be completed." }
            }

            assertEquals(producerCrc.await(), consumerCrc.await())
        }
    }

    @Test
    fun lastChunkReadTest() {
        val consumer = ApacheResponseConsumerDispatching(coroutineContext, null) { response, channel ->
            this.receivedResponse = response
            this.channel = channel
        }

        val decoder = object : ContentDecoder {
            private var completed = false

            override fun isCompleted(): Boolean = completed

            override fun read(dst: ByteBuffer?): Int {
                if (completed) return -1
                completed = true
                return 0
            }
        }

        consumer.consumeContent(decoder, AlwaysFailingIOControl())

        assertTrue(decoder.isCompleted)
        assertTrue(consumer.isDone)
    }

    private fun assertThread() {
        check(Thread.currentThread() === thread)
    }

    private fun response(): BasicHttpResponse =
        BasicHttpResponse(BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"))

    private class SuspendableInputIOControl : AlwaysFailingIOControl() {
        private val suspendedLatch = atomic(CompletableDeferred(Unit))

        val suspended: Boolean get() = suspendedLatch.value.isActive

        suspend fun waitNotSuspended(): Unit = suspendedLatch.value.await()

        fun assertSuspended() {
            assertTrue(suspended)
        }

        fun assertResumed() {
            assertFalse(suspended)
        }

        override fun requestInput() {
            suspendedLatch.value.complete(Unit)
        }

        override fun suspendInput() {
            val newLatch = CompletableDeferred<Unit>()

            suspendedLatch.getAndUpdate { before ->
                if (before.isActive) return
                newLatch
            }.complete(Unit)
        }
    }

    private open class AlwaysFailingIOControl : IOControl {
        override fun shutdown() {
            shouldNotBeInvoked()
        }

        override fun suspendOutput() {
            shouldNotBeInvoked()
        }

        override fun requestInput() {
            shouldNotBeInvoked()
        }

        override fun suspendInput() {
            shouldNotBeInvoked()
        }

        override fun requestOutput() {
            shouldNotBeInvoked()
        }

        private fun shouldNotBeInvoked(): Nothing {
            error("Should not be invoked")
        }
    }

    private inner class TestDecoder(val data: ByteArray) : ContentDecoder {
        private var current = 0

        /**
         * Shows how many bytes could be read or -1 if should be completed
         */
        var barrier = 0
            set(newValue) {
                assertThread()
                check(newValue >= field || (newValue == -1 && field == data.size))
                check(newValue <= data.size)
                field = newValue
            }

        val available: Int
            get() {
                val limit = when (barrier) {
                    -1 -> data.size
                    else -> barrier
                }

                val result = limit - current
                check(result >= 0)

                return result
            }

        override fun isCompleted(): Boolean {
            assertThread()
            return barrier == -1 && current == data.size
        }

        override fun read(dst: ByteBuffer): Int {
            assertThread()
            if (isCompleted) return -1

            val size = minOf(available, dst.remaining())

            if (size == 0) return 0

            dst.put(data, current, size)
            current += size

            return size
        }

        fun complete() {
            assertThread()

            if (barrier == data.size) {
                barrier = -1
            }
        }
    }

    private inner class ChannelDecoder : ContentDecoder {
        private val outgoing: Channel<ByteBuffer> = Channel(2)
        private val incoming: ReceiveChannel<ByteBuffer> = outgoing
        private val completed = atomic(false)

        private var current: ByteBuffer = ByteBuffer.allocate(0)

        fun failed(cause: Throwable) {
            outgoing.close(cause)
        }

        suspend fun send(data: ByteArray) {
            outgoing.send(ByteBuffer.wrap(data))
        }

        fun close() {
            outgoing.close()
        }

        override fun isCompleted(): Boolean {
            assertThread()
            return completed.value
        }

        override fun read(dst: ByteBuffer): Int {
            assertThread()
            if (completed.value) return -1

            while (!current.hasRemaining()) {
                val next = incoming.poll()
                if (next == null) {
                    if (incoming.isClosedForReceive) {
                        completed.value = true
                        return -1
                    }
                    return 0
                }
                current = next
            }

            return if (current.remaining() <= dst.remaining()) {
                val size = current.remaining()
                dst.put(current)
                size
            } else {
                val size = dst.remaining()
                val oldLimit = current.limit()
                current.limit(current.position() + dst.remaining())
                dst.put(current)
                current.limit(oldLimit)
                size
            }
        }
    }

    private object NonExistingDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val error = IllegalStateException("Shouldn't be dispatched to here")
            context.cancel(CancellationException("Failed to dispatch", error))
            throw error
        }
    }
}
