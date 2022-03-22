/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import kotlinx.atomicfu.locks.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import openssl.*
import kotlin.coroutines.*

//TODO handle BIO_eof
//TODO handle not fully write
//TODO handle partiol read
internal class SslPipe(
    private val reader: ByteReadChannel,
    private val writer: ByteWriteChannel,
    private val input: CPointer<BIO>,
    private val output: CPointer<BIO>
) {
    private val readerLock = Mutex()
    private var readerCont: CancellableContinuation<Int>? = null
    private var readerContLock = SynchronizedObject()

    private val writerLock = Mutex()

    //TODO: revisit
    suspend fun readAvailable(): Int {
        // if read is in progress, await it result
        if (!readerLock.tryLock()) return suspendCancellableCoroutine {
            synchronized(readerContLock) {
                readerCont = it
            }
        }

        try {
            val result = reader.read { source, start, endExclusive ->
                val bytesWritten = BIO_write(output, source.pointer + start, (endExclusive - start).toInt())
                //println("PIPE.READ: $bytesWritten")
                if (bytesWritten == -2) TODO("BIO operation isn't implemented")
                else if (bytesWritten < 0) 0
                else bytesWritten
            }
            synchronized(readerContLock) {
                readerCont?.resume(result)
                readerCont = null
            }

            return result
        } catch (cause: Throwable) {
            synchronized(readerContLock) {
                readerCont?.resumeWithException(cause)
                readerCont = null
            }
            throw cause
        } finally {
            readerLock.unlock()
        }
    }

    suspend fun writeFully(): Int = writerLock.withLock {
        val result = writer.write { freeSpace, startOffset, endExclusive ->
            val bytesRead = BIO_read(input, freeSpace.pointer + startOffset, (endExclusive - startOffset).toInt())
            //println("PIPE.WRITE: $bytesRead")
            if (bytesRead == -2) TODO("BIO operation isn't implemented")
            else if (bytesRead < 0) 0
            else bytesRead
        }
        writer.flush()
        return result
    }
}
