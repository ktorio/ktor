/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.eclipse.jetty.server.Request
import java.io.EOFException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.Duration

internal fun CoroutineScope.bodyReader(request: Request, log: Logger, idleTimeout: Duration?): WriterJob =
    writer(CoroutineName("jetty-request-reader")) {
        val contentLength = if (request.headers.contains(HttpHeaders.ContentLength)) {
            request.headers.get(HttpHeaders.ContentLength)?.toLong()
        } else {
            null
        }

        var bytesRead = 0L
        while (true) {
            when (val chunk = request.read()) {
                // nothing available, wait for more content
                null -> {
                    withTimeout(idleTimeout ?: Duration.INFINITE) {
                        suspendCancellableCoroutine { continuation ->
                            request.demand { continuation.resume(Unit) }
                        }
                    }
                }
                // read the chunk, exit and close channel if last chunk or failure
                else -> {
                    with(chunk) {
                        if (failure != null) {
                            if (isLast) {
                                throw failure
                            }
                            log.warn("Recoverable error reading request body; continuing", failure)
                        } else {
                            bytesRead += byteBuffer.remaining()
                            channel.writeFully(byteBuffer)
                            release()
                            if (contentLength != null && bytesRead > contentLength) {
                                channel.cancel(IOException("Request body exceeded content length limit"))
                            }
                            if (isLast) {
                                if (contentLength != null && bytesRead < contentLength) {
                                    channel.cancel(
                                        EOFException("Expected $contentLength bytes, received only $bytesRead")
                                    )
                                }
                                return@writer
                            }
                        }
                    }
                }
            }
        }
    }
