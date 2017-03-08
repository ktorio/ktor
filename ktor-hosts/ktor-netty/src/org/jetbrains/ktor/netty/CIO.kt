package org.jetbrains.ktor.netty

import io.netty.channel.*
import kotlin.coroutines.experimental.*

suspend fun ChannelFuture.suspendAwait() {
    if (isDone) return

    suspendCoroutine<Unit> { continuation ->
        addListener { f ->
            try {
                f.get()
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
                return@addListener
            }
            continuation.resume(Unit)
        }
    }
}