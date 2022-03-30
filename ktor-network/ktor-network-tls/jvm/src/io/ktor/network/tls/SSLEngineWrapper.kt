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
    private val output: ByteWriteChannel,
    private val debugString: String
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
            // println("[$debugString] WRAP_BEFORE: $wrapDestination")
            result = engine.wrap(wrapSource, wrapDestination)
            // println("[$debugString] WRAP_RESULT: $result")
            // println("[$debugString] WRAP_AFTER: $wrapDestination")
            if (result.status != SSLEngineResult.Status.BUFFER_OVERFLOW) break
            // println("[$debugString] WRAP_OVERFLOW: $wrapDestination")
            wrapDestination = bufferAllocator.reallocatePacket(wrapDestination, flip = true)
            // println("[$debugString] WRAP_OVERFLOW_REALLOCATE: $wrapDestination")
        }
        // println("[$debugString] WRAP_WRITE_BEFORE: $wrapDestination")
        if (result.bytesProduced() > 0) {
            wrapDestination.flip()
            // println("[$debugString] WRAP_WRITE: $wrapDestination")
            output.writeFully(wrapDestination)
            output.flush()
        }
        // println("[$debugString] WRAP_WRITE_AFTER: $wrapDestination")
        return result
    }

    suspend fun close(cause: Throwable?): SSLEngineResult = wrapLock.withLock {
        // println("[$debugString] CLOSE: $cause")
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
