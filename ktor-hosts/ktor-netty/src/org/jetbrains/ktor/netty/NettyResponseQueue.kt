package org.jetbrains.ktor.netty

import io.netty.channel.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException

internal class NettyResponseQueue(val context: ChannelHandlerContext) {
    private val q = LinkedBlockingQueue<CallElement>()
    private var cancellation: Throwable? = null

    fun started(call: ApplicationCall) {
        q.put(CallElement(call))
    }

    fun completed(call: ApplicationCall) {
        val s = q.poll()
        if (s?.call !== call) throw IllegalStateException("Wrong call in the queue")
        s.continuation = null

        q.peek()?.continuation?.resume(Unit)
    }

    suspend fun await(call: ApplicationCall) {
        if (q.peek()?.call === call) {
            return
        }

        suspendCancellableCoroutine<Unit> { c ->
            val s = q.firstOrNull { it.call === call } ?: throw IllegalStateException()
            s.continuation = c
            c.invokeOnCompletion {
                s.continuation = null
            }

            if (q.peek() === s) {
                c.resume(Unit)
            }
        }
    }

    fun cancel() {
        val t = CancellationException()
        cancellation = t

        do {
            val s = q.poll() ?: break
            s.continuation?.resumeWithException(t)
        } while (true)
    }

    private class CallElement(val call: ApplicationCall) {
        @Volatile
        var continuation: CancellableContinuation<Unit>? = null
    }
}