package io.ktor.server.netty.cio

import io.ktor.server.netty.*
import io.ktor.util.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.atomic.*

internal class NettyRequestQueue(internal val readLimit: Int, internal val runningLimit: Int) {
    init {
        require(readLimit > 0) { "readLimit should be positive: $readLimit" }
        require(runningLimit > 0) { "executeLimit should be positive: $runningLimit" }
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

    @UseExperimental(ExperimentalCoroutinesApi::class)
    fun canRequestMoreEvents(): Boolean = incomingQueue.isEmpty

    internal class CallElement(val call: NettyApplicationCall) : LockFreeLinkedListNode() {
        @kotlin.jvm.Volatile
        private var scheduled: Int = 0

        private val message: Job = call.response.responseMessage

        val isCompleted: Boolean get() = message.isCompleted

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
