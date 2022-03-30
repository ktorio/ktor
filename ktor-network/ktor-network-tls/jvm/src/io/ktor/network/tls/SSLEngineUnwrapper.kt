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
    private val input: ByteReadChannel,
    private val debugString: String
) {
    private val unwrapLock = Mutex()
    private var unwrapSource = bufferAllocator.allocatePacket(0)
    private var unwrapRemaining = 0

    //TODO revisit
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
                    synchronized(this) {
                        unwrapResultCont?.resume(null)
                        unwrapResultCont = null
                    }
                    return null
                }
            }

            //println("[$debugString] UNWRAP_INIT[DST]: $unwrapDestination")
            //println("[$debugString] UNWRAP_INIT[SRC]: $unwrapSource")
            while (true) {
                //println("[$debugString] UNWRAP_BEFORE[DST]: $unwrapDestination")
                //println("[$debugString] UNWRAP_BEFORE[SRC]: $unwrapSource")
                result = engine.unwrap(unwrapSource, unwrapDestination)
                //println("[$debugString] UNWRAP_RESULT: $result")
                //println("[$debugString] UNWRAP_AFTER[DST]: $unwrapDestination")
                //println("[$debugString] UNWRAP_AFTER[SRC]: $unwrapSource")

                when (result.status!!) {
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        //println("[$debugString] UNWRAP_UNDERFLOW_BEFORE[SRC]: $unwrapSource")
                        if (unwrapSource.limit() == unwrapSource.capacity()) {
                            //println("[$debugString] UNWRAP_UNDERFLOW_1")
                            //buffer is too small to read all needed data
                            unwrapSource = bufferAllocator.reallocatePacket(unwrapSource, flip = false)
                        } else {
                            //println("[$debugString] UNWRAP_UNDERFLOW_2")
                            //not all data received
                            unwrapSource.position(unwrapSource.limit())
                            unwrapSource.limit(unwrapSource.capacity())
                        }
                        //println("[$debugString] UNWRAP_UNDERFLOW_AFTER[SRC]: $unwrapSource")
                        if (!readData()) {
                            synchronized(this) {
                                unwrapResultCont?.resume(null)
                                unwrapResultCont = null
                            }
                            return null
                        }
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        //println("[$debugString] UNWRAP_OVERFLOW_BEFORE[DST]: $unwrapDestination")
                        unwrapDestination = bufferAllocator.reallocateApplication(unwrapDestination, flip = true)
                        //println("[$debugString] UNWRAP_OVERFLOW_AFTER[DST]: $unwrapDestination")
                    }
                    else -> break
                }
            }
            //println("[$debugString] UNWRAP_FINAL[DST]: $unwrapDestination")
            //println("[$debugString] UNWRAP_FINAL[SRC]: $unwrapSource")
            unwrapRemaining = unwrapSource.remaining()
            if (unwrapDestination !== initialUnwrapDestination) updateUnwrapDestination(unwrapDestination)
            synchronized(this) {
                unwrapResultCont?.resume(result)
                unwrapResultCont = null
            }
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

    private suspend fun readData(): Boolean {
        //println("[$debugString] UNWRAP_READ_BEFORE[SRC]: $unwrapSource")
        val read = input.readAvailable(unwrapSource)
        unwrapSource.flip()
        //println("[$debugString] UNWRAP_READ_AFTER[SRC]: $unwrapSource")

        //println("[$debugString] UNWRAP_READ_COMPLETE[SRC]: $unwrapSource")
        return read != -1
    }
}
