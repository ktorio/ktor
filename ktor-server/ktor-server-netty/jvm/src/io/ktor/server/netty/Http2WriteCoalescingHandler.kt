/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.util.concurrent.PromiseNotifier

/**
 * Gathers small outbound buffers (HTTP/2 frame headers and payloads) into one pooled I/O buffer
 * before they reach the TLS handler, so it never has to merge a composite buffer into a heap array.
 * Must be placed between the SSL handler and the HTTP/2 codec.
 */
internal class Http2WriteCoalescingHandler(private val capacity: Int = 16384) : ChannelDuplexHandler() {
    private var pending: ByteBuf? = null
    private val promises = ArrayList<ChannelPromise>()

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is ByteBuf || msg.readableBytes() > capacity) {
            flushPending(ctx)
            ctx.write(msg, promise)
            return
        }

        var buffer = pending
        if (buffer != null && buffer.writableBytes() < msg.readableBytes()) {
            flushPending(ctx)
            buffer = null
        }
        if (buffer == null) {
            buffer = ctx.alloc().ioBuffer(capacity)
            pending = buffer
        }

        buffer.writeBytes(msg)
        msg.release()
        if (!promise.isVoid) promises.add(promise)
    }

    override fun flush(ctx: ChannelHandlerContext) {
        flushPending(ctx)
        ctx.flush()
    }

    override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise) {
        flushPending(ctx)
        ctx.close(promise)
    }

    override fun disconnect(ctx: ChannelHandlerContext, promise: ChannelPromise) {
        flushPending(ctx)
        ctx.disconnect(promise)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        flushPending(ctx)
    }

    private fun flushPending(ctx: ChannelHandlerContext) {
        val buffer = pending ?: return
        pending = null

        if (promises.isEmpty()) {
            ctx.write(buffer, ctx.voidPromise())
            return
        }

        val combined = ctx.newPromise()
        combined.addListener(PromiseNotifier<Void, ChannelPromise>(*promises.toTypedArray()))
        promises.clear()
        ctx.write(buffer, combined)
    }
}
