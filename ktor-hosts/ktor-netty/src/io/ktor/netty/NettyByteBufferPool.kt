package io.ktor.netty

import io.netty.buffer.*
import io.netty.channel.*
import io.ktor.cio.*

internal class NettyByteBufferPool(val allocator: ByteBufAllocator): ByteBufferPool {
    constructor(context: ChannelHandlerContext) : this(context.alloc())

    override fun allocate(size: Int): PoolTicket {
        val heapBuffer = allocator.heapBuffer(size)
        return NettyBufferTicket(heapBuffer)
    }

    override fun release(buffer: PoolTicket) {
        val ticket = buffer as NettyBufferTicket
        ticket.bb.release()
        ticket.release()
    }

    internal class NettyBufferTicket(val bb: ByteBuf) : ReleasablePoolTicket(bb.nioBuffer(0, bb.capacity()))
}