package io.ktor.netty

import io.netty.channel.*
import kotlinx.coroutines.experimental.*
import io.ktor.application.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.*

internal class NettyResponseQueue(val context: ChannelHandlerContext) {
    private val q = LinkedBlockingQueue<CallElement>()
    @Volatile
    private var cancellation: Throwable? = null

    fun started(call: ApplicationCall) {
        q.put(CallElement(call))
    }

    fun completed(call: ApplicationCall) {
        val s = q.poll()
        if (s?.call !== call) throw IllegalStateException("Wrong call in the queue")
        s.continuation()

        q.peek()?.continuation()?.resume(Unit)
    }

    suspend fun await(call: ApplicationCall) {
        cancellation?.let { throw it }

        if (q.peek()?.call === call) {
            return
        }

        return suspendCancellableCoroutine { c ->
            val s = q.firstOrNull { it.call === call } ?: throw cancellation ?: IllegalStateException()
            s.suspended(c)

            c.invokeOnCompletion { t ->
                if (t != null) {
                    s.continuation()?.resumeWithException(t)
                } else {
                    s.continuation()?.resume(Unit)
                }
            }

            if (q.peek() === s) {
                s.continuation()?.resume(Unit)
            } else if (cancellation != null) {
                cancellation?.let { s.continuation()?.resumeWithException(it) }
            }
        }
    }

    fun cancel() {
        val t = CancellationException()
        cancellation = t

        do {
            val s = q.poll() ?: break
            s.continuation()?.resumeWithException(t)
        } while (true)
    }

    private class CallElement(val call: ApplicationCall) {
        @Volatile
        private var _continuation: CancellableContinuation<Unit>? = null

        fun suspended(c: CancellableContinuation<Unit>) {
            if (!Continuation.compareAndSet(this, null, c)) throw IllegalStateException("Already on await()")
        }

        @Suppress("UNCHECKED_CAST")
        fun continuation(): CancellableContinuation<Unit>? = Continuation.getAndSet(this, null) as CancellableContinuation<Unit>?

        companion object {
            private val Continuation = AtomicReferenceFieldUpdater.newUpdater(CallElement::class.java, CancellableContinuation::class.java, CallElement::_continuation.name)
        }
    }
}