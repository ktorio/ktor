/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.nio.*
import javax.net.ssl.*
import kotlin.coroutines.*

internal class SSLEngineUnwrapper(
    private val engine: SSLEngine,
    private val bufferAllocator: SSLEngineBufferAllocator,
    private val input: ByteReadChannel
) {
    private val unwrapLock = Mutex()
    private var unwrapSource = bufferAllocator.allocatePacket(0)
    private var unwrapRemaining = 0

    // continuation is needed when unwrap is requested both while reading more data and during handshake (started from writing data)
    //  in case unwrap is requested for reading more data DURING handshake, result should be handled by handshake loop
    private var unwrapResultCont: CancellableContinuation<SSLEngineResult?>? = null

    fun cancel(cause: Throwable?) {
        input.cancel(cause)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend inline fun readAndUnwrap(
        initialUnwrapDestination: ByteBuffer,
        updateUnwrapDestination: (ByteBuffer) -> Unit
    ): SSLEngineResult? {
        if (!unwrapLock.tryLock()) return suspendCancellableCoroutine {
            synchronized(this) {
                unwrapResultCont = it
            }
        }
        try {
            var unwrapDestination: ByteBuffer = initialUnwrapDestination
            var result: SSLEngineResult?

            if (unwrapRemaining > 0) {
                unwrapSource.compact()
                unwrapSource.flip()
            } else {
                unwrapSource.clear()
                if (!readData()) {
                    resumeUnwrapContinuation(null)
                    return null
                }
            }

            while (true) {
                result = engine.unwrap(unwrapSource, unwrapDestination)

                when (result.status!!) {
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        if (unwrapSource.limit() == unwrapSource.capacity()) {
                            // buffer is too small to read all needed data
                            unwrapSource = bufferAllocator.reallocatePacket(unwrapSource, flip = false)
                        } else {
                            // not all data received
                            unwrapSource.position(unwrapSource.limit())
                            unwrapSource.limit(unwrapSource.capacity())
                        }
                        if (!readData()) {
                            resumeUnwrapContinuation(null)
                            return null
                        }
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        unwrapDestination = bufferAllocator.reallocateApplication(unwrapDestination, flip = true)
                    }
                    else -> break
                }
            }
            unwrapRemaining = unwrapSource.remaining()
            if (unwrapDestination !== initialUnwrapDestination) updateUnwrapDestination(unwrapDestination)
            resumeUnwrapContinuation(result)
            return result
        } catch (cause: Throwable) {
            synchronized(this) {
                unwrapResultCont?.resumeWithException(cause)
                unwrapResultCont = null
            }
            throw cause
        } finally {
            unwrapLock.unlock()
        }
    }

    private fun resumeUnwrapContinuation(result: SSLEngineResult?) {
        synchronized(this) {
            unwrapResultCont?.resume(result)
            unwrapResultCont = null
        }
    }

    private suspend fun readData(): Boolean {
        val read = input.readAvailable(unwrapSource)
        unwrapSource.flip()
        return read != -1
    }
}
