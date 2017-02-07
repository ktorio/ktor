package org.jetbrains.ktor.netty

import io.netty.channel.*
import kotlin.coroutines.experimental.*

suspend fun ChannelFuture.suspendAwait() {
    if (isDone) return

    suspendCoroutine<Unit> { continuation ->
        addListener { f ->
            try {
                f.get()
                continuation.resume(Unit)
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
            }
        }
    }
}