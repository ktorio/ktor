/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.tls.internal.openssl.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.locks.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.coroutines.*

//TODO handle BIO_eof
//TODO handle not fully write
//TODO handle partiol read
internal class BIOPipe(
    ssl: CPointer<SSL>,
    connection: Connection,
    private val debugString: String
) {
    private val readerLock = Mutex()
    private var readerCont: CancellableContinuation<Int>? = null
    private var readerContLock = SynchronizedObject()

    private val writerLock = Mutex()

    private val sslOutput = BIO_new(BIO_s_mem())!!
    private val sslInput = BIO_new(BIO_s_mem())!!
    private val connectionInput = connection.input
    private val connectionOutput = connection.output

    init {
        SSL_set_bio(s = ssl, rbio = sslInput, wbio = sslOutput)
    }

    //TODO: revisit
    suspend fun readAvailable(): Int {
        // if read is in progress, await it result
        if (!readerLock.tryLock()) return suspendCancellableCoroutine {
            synchronized(readerContLock) {
                readerCont = it
            }
        }

        try {
            val result = connectionInput.read { source, start, endExclusive ->
                val bytesWritten = BIO_write(sslInput, source.pointer + start, (endExclusive - start).toInt())
                //println("[$debugString] PIPE.READ: $bytesWritten")
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
        //println("[$debugString] PIPE.WRITE.START")
        val result = connectionOutput.write { freeSpace, startOffset, endExclusive ->
            val bytesRead = BIO_read(sslOutput, freeSpace.pointer + startOffset, (endExclusive - startOffset).toInt())
            //println("[$debugString] PIPE.WRITE: $bytesRead")
            if (bytesRead == -2) TODO("BIO operation isn't implemented")
            else if (bytesRead < 0) 0
            else bytesRead
        }
        connectionOutput.flush()
        //println("[$debugString] PIPE.WRITE.FLUSHED")
        return result
    }
}
