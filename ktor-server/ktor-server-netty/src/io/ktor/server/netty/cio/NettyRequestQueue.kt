package io.ktor.server.netty.cio

import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.internal.*
import java.util.concurrent.atomic.*

internal class NettyRequestQueue(private val _readLimit: Int) {
    init {
        require(_readLimit > 0) { "readLimit should be positive: $_readLimit" }
//        require(executeLimit > 0) { "executeLimit should be positive: $executeLimit" }
    }

    private val incomingQueue = Channel<CallElement>(Channel.UNLIMITED)

    val elements: ReceiveChannel<CallElement> = incomingQueue

    fun schedule(call: NettyApplicationCall) {
        val element = CallElement(call)
        try {
            incomingQueue.offer(element)
        } catch (t: Throwable) {
            element.tryDispose()
        }
    }

    fun close() {
        incomingQueue.close()
    }

    fun cancel() {
        incomingQueue.close()

        while (true) {
            incomingQueue.poll()?.tryDispose() ?: break
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
}