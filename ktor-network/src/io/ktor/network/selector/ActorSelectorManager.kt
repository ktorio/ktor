package io.ktor.network.selector

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.internal.*
import java.io.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class ActorSelectorManager(dispatcher: CoroutineDispatcher) : SelectorManagerSupport(), Closeable {
    @Volatile
    private var selectorRef: Selector? = null

    private val wakeup = AtomicLong()

    @Volatile
    private var inSelect = false

    private val continuation = AtomicReference<Continuation<Selectable?>?>(null)

    @Volatile
    private var closed = false

    private val mb = LockFreeMPSCQueue<Selectable>()

    init {
        launch(dispatcher) {
            provider.openSelector()!!.use { selector ->
                selectorRef = selector
                try {
                    process(mb, selector)
                } catch (t: Throwable) {
                    closed = true
                    mb.close()
                    cancelAllSuspensions(selector, t)
                } finally {
                    closed = true
                    mb.close()
                    selectorRef = null
                    cancelAllSuspensions(selector, null)
                }

                while (true) {
                    val m = mb.removeFirstOrNull() ?: break
                    cancelAllSuspensions(m, ClosedSendChannelException("Failed to apply interest: selector closed"))
                }
            }
        }
    }

    private suspend fun process(mb: LockFreeMPSCQueue<Selectable>, selector: Selector) {
        while (!closed) {
            processInterests(mb, selector)

            if (pending > 0) {
                if (select(selector) > 0) {
                    handleSelectedKeys(selector.selectedKeys(), selector.keys())
                } else {
                    val received = mb.removeFirstOrNull()
                    if (received != null) applyInterest(selector, received)
                    else yield()
                }
            } else if (cancelled > 0) {
                selector.selectNow()
                if (pending > 0) {
                    handleSelectedKeys(selector.selectedKeys(), selector.keys())
                }
            } else {
                val received = mb.receiveOrNull() ?: break
                applyInterest(selector, received)
            }
        }
    }

    private fun select(selector: Selector): Int {
        inSelect = true
        return if (wakeup.get() == 0L) {
            val count = selector.select(500L)
            inSelect = false
            count
        } else {
            inSelect = false
            wakeup.set(0)
            selector.selectNow()
        }
    }

    private fun selectWakeup() {
        if (wakeup.incrementAndGet() == 1L && inSelect) {
            selectorRef?.wakeup()
        }
    }

    private fun processInterests(mb: LockFreeMPSCQueue<Selectable>, selector: Selector) {
        while (true) {
            val selectable = mb.removeFirstOrNull() ?: break
            applyInterest(selector, selectable)
        }
    }

    override fun notifyClosed(s: Selectable) {
        cancelAllSuspensions(s, ClosedChannelException())
        selectorRef?.let { selector ->
            s.channel.keyFor(selector)?.let { k ->
                k.cancel()
                selectWakeup()
            }
        }
    }

    override fun publishInterest(selectable: Selectable) {
        try {
            if (mb.addLast(selectable)) {
                val cont = continuation.getAndSet(null)
                if (cont != null) {
                    cont.resume(null)
                } else {
                    selectWakeup()
                }
            }
            else throw IOException("Failed to publish interest to the queue")
        } catch (t: Throwable) {
            cancelAllSuspensions(selectable, t)
        }
    }


    private suspend fun LockFreeMPSCQueue<Selectable>.receiveOrNull(): Selectable? {
        return removeFirstOrNull() ?: receiveOrNullSuspend()
    }

    private suspend fun LockFreeMPSCQueue<Selectable>.receiveOrNullSuspend(): Selectable? {
        while (true) {
            val m = removeFirstOrNull()
            if (m != null) return m

            if (closed) return null

            val m3 = suspendCoroutineUninterceptedOrReturn<Selectable?> {
                continuation.set(it)

                val m2 = removeFirstOrNull()
                if ((m2 != null || closed) && continuation.compareAndSet(it, null)) m2
                else COROUTINE_SUSPENDED
            }

            if (m3 != null) return m3
        }
    }

    override fun close() {
        closed = true
        mb.close()
        val cont = continuation.getAndSet(null)
        if (cont != null) {
            cont.resume(null)
        } else {
            selectWakeup()
        }
    }
}
