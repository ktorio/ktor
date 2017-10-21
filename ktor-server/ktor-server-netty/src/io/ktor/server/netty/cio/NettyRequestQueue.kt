package io.ktor.server.netty.cio

import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.internal.*
import java.util.concurrent.atomic.*

internal class NettyRequestQueue(private val limit: Int) {
    init {
        require(limit > 0)
    }

    private val queue = LockFreeLinkedListHead()
    @Volatile
    private var counter = 0

    @Volatile
    private var receiver: CancellableContinuation<Unit>? = null

    @Volatile
    private var closed = 0

    fun schedule(call: NettyApplicationCall) {
        if (closed != 0) { // fast path if closed
            call.dispose() // see note below
            return
        }

        val element = CallElement(call)
        val n = Counter.incrementAndGet(this)

        if (!queue.addLastIfPrev(element, { it !is CloseElement })) {
            // counter is already incremented but we can ignore it as the queue is already closed
            call.dispose() // it is safe here to dispose as call handling is not yet started
        } else if (n < limit) {
            element.ensureRunning()
            call.context.read()
            resume()
        } else if (n == limit) {
            element.ensureRunning()
        }
    }

    fun cancel() {
        if (Closed.compareAndSet(this, 0, 1)) {
            queue.addLast(CloseElement())

            while (true) {
                val element = queue.removeFirstIfIsInstanceOf<CallElement>() ?: break
                element.tryDispose()
            }

            resume()
        }
    }

    suspend tailrec fun receiveOrNull(): NettyApplicationCall? {
        val element = queue.removeFirstIfIsInstanceOf<CallElement>()

        if (element != null) {
            return returnCall(element) ?: return receiveOrNull()
        }

        if (closed != 0) {
            return null
        }

        return receiveOrNullSuspend()
    }

    fun canRequestMoreEvents(): Boolean = counter <= limit

    private tailrec suspend fun receiveOrNullSuspend(): NettyApplicationCall? {
        val element = queue.removeFirstIfIsInstanceOf<CallElement>()

        if (element != null) {
            return returnCall(element) ?: return receiveOrNullSuspend()
        }

        if (closed != 0) {
            return null
        }

        suspendCancellableCoroutine<Unit>(holdCancellability = true) { c ->
            if (!Receiver.compareAndSet(this, null, c)) throw IllegalStateException("receive already pending")

            if (closed != 0 || queue.next is CallElement) {
                if (Receiver.compareAndSet(this, c, null)) {
                    c.resume(Unit)
                    return@suspendCancellableCoroutine
                }
            }

            c.invokeOnCompletion(true) {
                Receiver.compareAndSet(this, c, null)
            }
        }

        return receiveOrNullSuspend()
    }

    private fun resume() {
        Receiver.getAndSet(this, null)?.resume(Unit)
    }

    private fun returnCall(element: CallElement): NettyApplicationCall? {
        if (closed != 0) {
            element.tryDispose()
            return null
        }

        val n = Counter.getAndDecrement(this)
        if (n == limit) {
            element.call.context.read()
            (queue.next as? CallElement)?.ensureRunning()
        }

        return if (element.ensureRunning()) element.call else null
    }

    private class CloseElement : LockFreeLinkedListNode()
    private class CallElement(val call: NettyApplicationCall) : LockFreeLinkedListNode() {
        @Volatile
        private var scheduled: Int = 0

        fun ensureRunning(): Boolean {
            if (Scheduled.compareAndSet(this, 0, 1)) {
                call.context.fireChannelRead(call)
                return true
            }

            return scheduled == 1
        }

        fun tryDispose() {
            if (Scheduled.compareAndSet(this, 0, 2)) {
                call.dispose()
            }
        }

        companion object {
            private val Scheduled = AtomicIntegerFieldUpdater.newUpdater(CallElement::class.java, CallElement::scheduled.name)!!
        }
    }

    companion object {
        private val Counter = AtomicIntegerFieldUpdater.newUpdater(NettyRequestQueue::class.java, NettyRequestQueue::counter.name)!!
        private val Closed = AtomicIntegerFieldUpdater.newUpdater(NettyRequestQueue::class.java, NettyRequestQueue::closed.name)!!

        @Suppress("UNCHECKED_CAST")
        private val Receiver = AtomicReferenceFieldUpdater.newUpdater(NettyRequestQueue::class.java, CancellableContinuation::class.java, NettyRequestQueue::receiver.name) as AtomicReferenceFieldUpdater<NettyRequestQueue, CancellableContinuation<Unit>?>
    }
}