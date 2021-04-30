/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.ByteBufferChannel
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.nio.*

internal class JoiningState(val delegatedTo: ByteBufferChannel, val delegateClose: Boolean) {
    private val _closeWaitJob = atomic<Job?>(null)
    private val closed = atomic(0)

    private val closeWaitJob: Job
        get() {
            while (true) {
                val current = _closeWaitJob.value
                if (current != null) return current
                val newJob = Job()
                if (_closeWaitJob.compareAndSet(null, newJob)) {
                    if (closed.value == 1) newJob.cancel()
                    return newJob
                }
            }
        }

    fun complete() {
        closed.value = 1
        _closeWaitJob.getAndSet(null)?.cancel()
    }

    suspend fun awaitClose() {
        if (closed.value == 1) return
        return closeWaitJob.join()
    }
}

@Suppress("DEPRECATION")
internal object TerminatedLookAhead : LookAheadSuspendSession {
    override fun consumed(n: Int) {
        if (n > 0) throw IllegalStateException("Unable to mark $n bytes consumed for already terminated channel")
    }

    override fun request(skip: Int, atLeast: Int): ByteBuffer? = null

    override suspend fun awaitAtLeast(n: Int): Boolean {
        require(n >= 0) { "atLeast parameter shouldn't be negative: $n" }
        require(n <= 4088) { "atLeast parameter shouldn't be larger than max buffer size of 4088: $n" }

        return false
    }
}

@Suppress("DEPRECATION")
internal class FailedLookAhead(val cause: Throwable) : LookAheadSuspendSession {
    override fun consumed(n: Int) = throw cause
    override fun request(skip: Int, atLeast: Int): ByteBuffer = throw cause
    override suspend fun awaitAtLeast(n: Int): Boolean = throw cause
}

internal class ClosedElement(val cause: Throwable?) {
    val sendException: Throwable
        get() = cause ?: ClosedWriteChannelException("The channel was closed")

    override fun toString(): String = "Closed[$sendException]"

    companion object {
        val EmptyCause = ClosedElement(null)
    }
}
