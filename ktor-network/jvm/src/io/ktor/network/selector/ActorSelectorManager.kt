/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Default CIO selector manager implementation
 */
@Suppress("BlockingMethodInNonBlockingContext")
public class ActorSelectorManager(context: CoroutineContext) : SelectorManagerSupport(), Closeable, CoroutineScope {
    @Volatile
    private var selectorRef: Selector? = null

    private val wakeup = AtomicLong()

    @Volatile
    private var inSelect = false

    private val continuation = ContinuationHolder<Unit, Continuation<Unit>>()

    @Volatile
    private var closed = false

    private val mb = LockFreeMPSCQueue<Selectable>()

    override val coroutineContext: CoroutineContext = context + CoroutineName("selector")

    init {
        launch {
            val selector = provider.openSelector() ?: error("openSelector() = null")
            selectorRef = selector
            try {
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
            } finally {
                selector.close()
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
                } else {
                    cancelled = 0
                }
            } else {
                val received = mb.receiveOrNull() ?: break
                applyInterest(selector, received)
            }
        }
    }

    private suspend fun select(selector: Selector): Int {
        inSelect = true
        dispatchIfNeeded()
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

    private suspend inline fun dispatchIfNeeded() {
        yield() // it will always redispatch it to the right thread
        // it is very important here because we do _unintercepted_ resume that may lead to blocking on a wrong thread
        // that may cause deadlock
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

    /**
     * Publish current [selectable] interest
     */
    override fun publishInterest(selectable: Selectable) {
        try {
            if (mb.addLast(selectable)) {
                if (!continuation.resume(Unit)) {
                    selectWakeup()
                }
            } else if (selectable.channel.isOpen) throw ClosedSelectorException()
            else throw ClosedChannelException()
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

            suspendCoroutineUninterceptedOrReturn<Unit> {
                continuation.suspendIf(it) { isEmpty && !closed } ?: Unit
            }
        }
    }

    /**
     * Close selector manager and release all resources
     */
    override fun close() {
        closed = true
        mb.close()
        if (!continuation.resume(Unit)) {
            selectWakeup()
        }
    }

    private class ContinuationHolder<R, C : Continuation<R>> {
        private val ref = AtomicReference<C?>(null)

        fun resume(value: R): Boolean {
            val continuation = ref.getAndSet(null)
            if (continuation != null) {
                continuation.resume(value)
                /** we resume unintercepted, see [dispatchIfNeeded] */
                return true
            }

            return false
        }

        /**
         * @return `null` if not suspended due to failed condition or `COROUTINE_SUSPENDED` if successfully applied
         */
        inline fun suspendIf(continuation: C, condition: () -> Boolean): Any? {
            if (!condition()) return null
            if (!ref.compareAndSet(null, continuation)) {
                throw IllegalStateException("Continuation is already set")
            }
            if (!condition() && ref.compareAndSet(continuation, null)) return null
            return COROUTINE_SUSPENDED
        }
    }
}
