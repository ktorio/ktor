/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import kotlin.time.Duration

internal fun CoroutineScope.bodyWriter(response: Response, idleTimeout: Duration?): ReaderJob =
    reader(CoroutineName("jetty-response-writer")) {
        var count = 0
        val buffer = bufferPool.borrow()
        try {
            while (true) {
                when (val current = channel.readAvailable(buffer)) {
                    -1 -> break
                    0 -> continue
                    else -> count += current
                }

                withTimeout(idleTimeout ?: Duration.INFINITE) {
                    suspendCancellableCoroutine { continuation ->
                        response.write(
                            channel.isClosedForRead,
                            buffer.flip(),
                            continuation.asCallback()
                        )
                    }
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
