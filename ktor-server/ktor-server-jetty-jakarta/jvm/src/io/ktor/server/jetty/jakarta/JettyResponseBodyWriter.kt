/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.thread.Invocable
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal fun CoroutineScope.bodyWriter(response: Response): ReaderJob =
    reader(CoroutineName("jetty-response-writer")) {
        var count = 0
        val buffer = bufferPool.borrow()
        var continuation: Continuation<Unit>? = null
        val callback = object : Callback {
            override fun succeeded() {
                continuation?.resume(Unit)
            }
            override fun failed(x: Throwable?) {
                channel.cancel(x ?: IOException("Failed to write body"))
            }
            override fun getInvocationType(): Invocable.InvocationType? =
                Invocable.InvocationType.NON_BLOCKING
        }
        try {
            while (true) {
                when (val current = channel.readAvailable(buffer)) {
                    -1 -> break
                    0 -> continue
                    else -> count += current
                }
                // Suspend till write completes, only 1 write at a time allowed
                suspendCancellableCoroutine { cont ->
                    continuation = cont
                    response.write(
                        channel.isClosedForRead,
                        buffer.flip(),
                        callback
                    )
                }
                buffer.compact()
            }
        } catch (cause: Throwable) {
            channel.cancel(cause)
        } finally {
            bufferPool.recycle(buffer)
            runCatching {
                if (!response.isCommitted) {
                    response.write(true, emptyBuffer, Callback.NOOP)
                }
            }
        }
    }
