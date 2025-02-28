/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.io.EndPoint
import kotlin.coroutines.CoroutineContext

public fun CoroutineScope.connect(
    endpoint: EndPoint,
    coroutineNamePrefix: String,
    bufferPool: ByteBufferPool,
    coroutineContext: CoroutineContext
): Pair<WriterJob, ReaderJob> {
    val inputJob: WriterJob =
        writer(Dispatchers.IO + coroutineContext + CoroutineName("$coroutineNamePrefix-input")) {
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    suspendCancellableCoroutine { continuation ->
                        endpoint.tryFillInterested(continuation.asCallback())
                    }
                    when (endpoint.fill(buffer)) {
                        -1 -> break
                        0 -> continue
                        else -> {}
                    }
                    channel.writeFully(buffer.flip())
                    buffer.compact()
                }
            } finally {
                bufferPool.recycle(buffer)
            }
        }

    val outputJob: ReaderJob =
        reader(Dispatchers.IO + coroutineContext + CoroutineName("$coroutineNamePrefix-output")) {
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    when (channel.readAvailable(buffer)) {
                        -1 -> break
                        0 -> continue
                        else -> {}
                    }
                    suspendCancellableCoroutine<Unit> { continuation ->
                        endpoint.write(continuation.asCallback(), buffer.flip())
                    }
                    buffer.compact()
                }
            } finally {
                bufferPool.recycle(buffer)
            }
        }

    return inputJob to outputJob
}
