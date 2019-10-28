/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.util.*
import kotlinx.coroutines.*
import java.nio.channels.*
import java.nio.channels.spi.*
import kotlin.coroutines.*

/**
 * Base class for NIO selector managers
 */
@KtorExperimentalAPI
abstract class SelectorManagerSupport internal constructor() : SelectorManager {
    final override val provider: SelectorProvider = SelectorProvider.provider()
    /**
     * Number of pending selectables
     */
    protected var pending = 0

    /**
     * Number of cancelled keys
     */
    protected var cancelled = 0

    /**
     * Publish current [selectable] interest, any thread
     */
    protected abstract fun publishInterest(selectable: Selectable)

    final override suspend fun select(selectable: Selectable, interest: SelectInterest) {
        require(selectable.interestedOps and interest.flag != 0)

        suspendCancellableCoroutine<Unit> { c ->
//            val c = base.tracked()  // useful for debugging

            c.invokeOnCancellation {
                // TODO: We've got a race here (and exception erasure)!
//                selectable.dispose()
            }
            selectable.suspensions.addSuspension(interest, c)

            if (!c.isCancelled) {
                publishInterest(selectable)
            }
        }
    }

    /**
     * Handle selected keys clearing [selectedKeys] set
     */
    protected fun handleSelectedKeys(selectedKeys: MutableSet<SelectionKey>, keys: Set<SelectionKey>) {
        val selectedCount = selectedKeys.size
        pending = keys.size - selectedCount
        cancelled = 0

        if (selectedCount > 0) {
            val iter = selectedKeys.iterator()
            while (iter.hasNext()) {
                val k = iter.next()
                handleSelectedKey(k)
                iter.remove()
            }
        }
    }

    /**
     * Handles particular selected key
     */
    protected fun handleSelectedKey(key: SelectionKey) {
        try {
            val readyOps = key.readyOps()
            val interestOps = key.interestOps()

            val subj = key.subject
            if (subj == null) {
                key.cancel()
                cancelled++
            } else {
                val unit = Unit
                subj.suspensions.invokeForEachPresent(readyOps) { resume(unit) }

                val newOps = interestOps and readyOps.inv()
                if (newOps != interestOps) {
                    key.interestOps(newOps)
                }

                if (newOps != 0) {
                    pending++
                }
            }
        } catch (t: Throwable) {
            // cancelled or rejected on resume?
            key.cancel()
            cancelled++
            key.subject?.let { subj ->
                cancelAllSuspensions(subj, t)
                key.subject = null
            }
        }
    }

    /**
     * Applies selectable's current interest (should be invoked in selection thread)
     */
    protected fun applyInterest(selector: Selector, s: Selectable) {
        try {
            val channel = s.channel
            val key = channel.keyFor(selector)
            val ops = s.interestedOps

            if (key == null) {
                if (ops != 0) {
                    channel.register(selector, ops, s)
                }
            } else {
                if (key.interestOps() != ops) {
                    key.interestOps(ops)
                }
            }

            if (ops != 0) {
                pending++
            }
        } catch (t: Throwable) {
            s.channel.keyFor(selector)?.cancel()
            cancelAllSuspensions(s, t)
        }
    }

    /**
     * Notify selectable's closure
     */
    protected fun notifyClosedImpl(selector: Selector, key: SelectionKey, attachment: Selectable) {
        cancelAllSuspensions(attachment, ClosedChannelException())

        key.subject = null
        selector.wakeup()
    }

    /**
     * Cancel all selectable's suspensions with the specified exception
     */
    protected fun cancelAllSuspensions(attachment: Selectable, t: Throwable) {
        attachment.suspensions.invokeForEachPresent {
            resumeWithException(t)
        }
    }

    /**
     * Cancel all suspensions with the specified exception, reset all interests
     */
    protected fun cancelAllSuspensions(selector: Selector, t: Throwable?) {
        val cause = t ?: ClosedSelectorCancellationException()

        selector.keys().forEach { k ->
            try {
                if (k.isValid) k.interestOps(0)
            } catch (ignore: CancelledKeyException) {
            }
            (k.attachment() as? Selectable)?.let { cancelAllSuspensions(it, cause) }
            k.cancel()
        }
    }

    private var SelectionKey.subject: Selectable?
        get() = attachment() as? Selectable
        set(newValue) {
            attach(newValue)
        }

    class ClosedSelectorCancellationException : CancellationException("Closed selector")
}
