/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.http2.api.Stream
import org.eclipse.jetty.http2.frames.DataFrame
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val EmptyByteBuffer = ByteBuffer.allocate(0)!!

internal class JettyHttp2Request(private val stream: Stream) : Callback {
    private val deferred = atomic<CancellableContinuation<Unit>?>(null)

    suspend fun write(src: ByteBuffer) = suspendCancellableCoroutine { continuation ->
        deferred.value = continuation

        val frame = DataFrame(stream.id, src, false)
        stream.data(frame, this)
    }

    override fun succeeded() {
        deferred.getAndSet(null)!!.resume(Unit)
    }

    override fun failed(cause: Throwable) {
        deferred.getAndSet(null)!!.resumeWithException(cause)
    }

    fun endBody() {
        stream.data(DataFrame(stream.id, EmptyByteBuffer, true), Callback.NOOP)
    }
}
