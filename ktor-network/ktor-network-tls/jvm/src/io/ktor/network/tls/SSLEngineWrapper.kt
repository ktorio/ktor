/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import kotlinx.coroutines.sync.*
import java.nio.*
import javax.net.ssl.*

internal class SSLEngineWrapper(
    private val engine: SSLEngine,
    private val bufferAllocator: SSLEngineBufferAllocator,
    private val output: ByteWriteChannel
) {
    private val wrapLock = Mutex()
    private var wrapDestination = bufferAllocator.allocatePacket(0)

    suspend fun wrapAndWrite(wrapSource: ByteBuffer): SSLEngineResult = wrapLock.withLock {
        wrapAndWriteX(wrapSource)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun wrapAndWriteX(wrapSource: ByteBuffer): SSLEngineResult {
        var result: SSLEngineResult
        wrapDestination.clear()
        while (true) {
            result = engine.wrap(wrapSource, wrapDestination)
            if (result.status != SSLEngineResult.Status.BUFFER_OVERFLOW) break
            wrapDestination = bufferAllocator.reallocatePacket(wrapDestination, flip = true)
        }
        if (result.bytesProduced() > 0) {
            wrapDestination.flip()
            output.writeFully(wrapDestination)
            output.flush()
        }
        return result
    }

    suspend fun close(cause: Throwable?): SSLEngineResult = wrapLock.withLock {
        val temp = bufferAllocator.allocateApplication(0)
        var result: SSLEngineResult
        do {
            result = wrapAndWriteX(temp)
        } while (
            result.status != SSLEngineResult.Status.CLOSED &&
            !(result.status == SSLEngineResult.Status.OK && result.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
        )

        output.close(cause)

        result
    }
}
