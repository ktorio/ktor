package io.ktor.server.netty.cio

import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.internal.*
import java.util.concurrent.atomic.*

private const val StateRunning = 0
private const val StateClosed = 1
private const val StateCancelled = 2

internal class NettyRequestQueue(private val _readLimit: Int) {
    init {
        require(_readLimit > 0) { "readLimit should be positive: $_readLimit" }
//        require(executeLimit > 0) { "executeLimit should be positive: $executeLimit" }
    }

    @Volatile
    private var counter = 0
    private val incomingQueue = Channel<CallElement>(Channel.UNLIMITED)
    private val readyQueue = Channel<CallElement>(Channel.UNLIMITED)

    val elements: ReceiveChannel<CallElement> = readyQueue

    init {
        launch(Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try {
                while (state == StateRunning) {
                    val e = incomingQueue.receiveOrNull() ?: break
                    val counter = Counter.decrementAndGet(this@NettyRequestQueue)
                    if (counter < _readLimit) e.call.context.read()

                    try {
                        check(readyQueue.offer(e))
                        if (state != StateRunning) {
                            e.tryDispose()
                        }
                    } catch (t: Throwable) {
                        e.tryDispose()
                        throw t
                    }
                }
            } finally {
                if (state == StateClosed) {
                    readyQueue.close()
                } else {
                    readyQueue.cancel()
                }
            }
        }
    }

    @Volatile
    private var state = StateRunning

    fun schedule(call: NettyApplicationCall) {
        if (state != StateRunning) { // fast path if closed
            call.dispose() // see note below
            return
        }

        Counter.incrementAndGet(this)
        val element = CallElement(call)
        incomingQueue.offer(element)

        if (state != StateRunning) {
            element.tryDispose()
        }
    }

    fun close() {
        if (State.compareAndSet(this, StateRunning, StateClosed)) {
            incomingQueue.close()
        }
    }

    fun cancel() {
        if (State.compareAndSet(this, StateRunning, StateCancelled)) {
            incomingQueue.close()

            while (true) {
                incomingQueue.poll()?.tryDispose() ?: break
            }
        }
    }

    fun canRequestMoreEvents(): Boolean = incomingQueue.isEmpty

    internal class CallElement(val call: NettyApplicationCall) : LockFreeLinkedListNode() {
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
        private val State = AtomicIntegerFieldUpdater.newUpdater(NettyRequestQueue::class.java, NettyRequestQueue::state.name)!!
    }
}