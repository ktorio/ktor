/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.timeout.WriteTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Tests [NettyIdleStateHandler] on an [EmbeddedChannel] with a frozen clock, so idle timeouts run in virtual time.
 */
class NettyIdleStateHandlerTest {

    @Test
    fun `writer idle - stalled write fails the channel`() {
        withChannel(NettyIdleStateHandler(0, 1, 0) { true }) { channel ->
            // Not flushed, so the data stays pending without any progress
            channel.write(bytes(1024))
            channel.advanceSeconds(2)

            assertFailsWith<WriteTimeoutException> { channel.checkException() }
        }
    }

    @Test
    fun `writer idle - slow write that keeps progressing is not failed`() {
        withChannel(NettyIdleStateHandler(0, 1, 0) { true }, SlowWriteChannel(bytesPerFlush = 100)) { channel ->
            // A single large write, like a ByteArray response, delivered a little at a time
            channel.writeAndFlush(bytes(10_000))
            repeat(10) {
                channel.advanceSeconds(1)
                channel.flush()
            }

            channel.checkException()
            assertTrue(channel.isOpen)
        }
    }

    @Test
    fun `writer idle - nothing pending keeps the connection open`() {
        withChannel(NettyIdleStateHandler(0, 1, 0) { false }) { channel ->
            channel.advanceSeconds(5)

            channel.checkException()
            assertTrue(channel.isOpen)
        }
    }

    @Test
    fun `reader idle - closes the connection when no call is active`() {
        withChannel(NettyIdleStateHandler(1, 0, 0) { false }) { channel ->
            channel.advanceSeconds(1)

            assertFalse(channel.isOpen)
        }
    }

    @Test
    fun `reader idle - keeps the connection open while a call is active`() {
        withChannel(NettyIdleStateHandler(1, 0, 0) { true }) { channel ->
            channel.advanceSeconds(5)

            assertTrue(channel.isOpen)
        }
    }

    @Test
    fun `reader idle - keeps the connection open while response data is pending`() {
        withChannel(NettyIdleStateHandler(1, 0, 0) { false }) { channel ->
            channel.write(bytes(1024))
            channel.advanceSeconds(5)

            assertTrue(channel.isOpen)
        }
    }

    @Test
    fun `all idle - closes the connection when nothing is pending`() {
        withChannel(NettyIdleStateHandler(0, 0, 1) { true }) { channel ->
            channel.advanceSeconds(1)

            assertFalse(channel.isOpen)
        }
    }

    @Test
    fun `all idle - keeps the connection open while response data is pending`() {
        withChannel(NettyIdleStateHandler(0, 0, 1) { true }) { channel ->
            channel.write(bytes(1024))
            channel.advanceSeconds(5)

            assertTrue(channel.isOpen)
        }
    }

    @Test
    fun `disabled timeouts never close the connection`() {
        withChannel(NettyIdleStateHandler(0, 0, 0) { false }) { channel ->
            channel.write(bytes(1024))
            channel.advanceSeconds(60)

            channel.checkException()
            assertTrue(channel.isOpen)
        }
    }

    @Test
    fun `idle events are not passed down the pipeline`() {
        val events = mutableListOf<Any>()
        withChannel(NettyIdleStateHandler(1, 1, 1) { true }) { channel ->
            channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
                    events += evt
                }
            })

            channel.advanceSeconds(5)

            assertEquals(emptyList(), events)
        }
    }

    private fun withChannel(
        handler: NettyIdleStateHandler,
        channel: EmbeddedChannel = EmbeddedChannel(),
        block: (EmbeddedChannel) -> Unit
    ) {
        // Freeze the clock before the handler starts its timers
        channel.freezeTime()
        channel.pipeline().addLast(handler)
        try {
            block(channel)
        } finally {
            channel.finishAndReleaseAll()
        }
    }

    private fun EmbeddedChannel.advanceSeconds(seconds: Long) {
        repeat(seconds.toInt()) {
            advanceTimeBy(1, TimeUnit.SECONDS)
            runScheduledPendingTasks()
        }
    }

    private fun bytes(size: Int): ByteBuf = Unpooled.wrappedBuffer(ByteArray(size))

    /**
     * Writes only [bytesPerFlush] bytes of the current message on each flush, like a socket to a slow client.
     */
    private class SlowWriteChannel(private val bytesPerFlush: Int) : EmbeddedChannel() {
        override fun doWrite(buffer: ChannelOutboundBuffer) {
            val message = buffer.current() as? ByteBuf ?: return
            buffer.removeBytes(minOf(bytesPerFlush, message.readableBytes()).toLong())
        }
    }
}
