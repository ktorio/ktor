/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import java.nio.*
import javax.net.ssl.*

internal class SSLEngineBufferAllocator(private val engine: SSLEngine) {
    private var packetBufferSize = 0
    private var applicationBufferSize = 0

    fun allocatePacket(length: Int): ByteBuffer = allocate(
        length,
        get = { packetBufferSize },
        set = { packetBufferSize = it },
        new = { engine.session.packetBufferSize }
    )

    fun allocateApplication(length: Int): ByteBuffer = allocate(
        length,
        get = { applicationBufferSize },
        set = { applicationBufferSize = it },
        new = { engine.session.applicationBufferSize }
    )

    fun reallocatePacket(buffer: ByteBuffer, flip: Boolean): ByteBuffer =
        reallocate(buffer, flip, ::allocatePacket)

    fun reallocateApplication(buffer: ByteBuffer, flip: Boolean): ByteBuffer =
        reallocate(buffer, flip, ::allocateApplication)

    private inline fun allocate(
        length: Int,
        get: () -> Int,
        set: (Int) -> Unit,
        new: () -> Int
    ): ByteBuffer = synchronized(this) {
        if (get() == 0) {
            set(new())
        }
        if (length > get()) {
            set(length)
        }
        return ByteBuffer.allocate(get())
    }

    private inline fun reallocate(
        buffer: ByteBuffer,
        flip: Boolean,
        allocate: (length: Int) -> ByteBuffer
    ): ByteBuffer {
        val newSize = buffer.capacity() * 2
        val new = allocate(newSize)
        if (flip) {
            new.flip()
        }
        new.put(buffer)
        return new
    }
}
