package io.ktor.network.selector

import kotlinx.coroutines.experimental.*
import java.nio.channels.*
import java.nio.channels.spi.*

abstract class SelectorManagerSupport internal constructor() : SelectorManager {
    final override val provider: SelectorProvider = SelectorProvider.provider()
    protected var pending = 0
    protected var cancelled = 0

    protected abstract fun publishInterest(selectable: Selectable)

    final override suspend fun select(selectable: Selectable, interest: SelectInterest) {
        require(selectable.interestedOps and interest.flag != 0)

        suspendCancellableCoroutine<Unit> { c ->
//            val c = base.tracked()  // useful for debugging

            selectable.suspensions.addSuspension(interest, c)
            c.disposeOnCancel(selectable)

            if (!c.isCancelled) {
                publishInterest(selectable)
            }
        }
    }

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

    protected fun notifyClosedImpl(selector: Selector, key: SelectionKey, attachment: Selectable) {
        cancelAllSuspensions(attachment, ClosedChannelException())

        key.subject = null
        selector.wakeup()
    }

    protected fun cancelAllSuspensions(attachment: Selectable, t: Throwable) {
        attachment.suspensions.invokeForEachPresent {
            try {
                resumeWithException(t)
            } catch (t2: Throwable) { // rejected?
                t2.printStackTrace()
            }
        }
    }

    protected fun cancelAllSuspensions(selector: Selector, t: Throwable?) {
        val cause = t ?: ClosedSelectorException()

        selector.keys().forEach { k ->
            k.cancel()
            (k.attachment() as? Selectable)?.let { cancelAllSuspensions(it, cause) }
        }
    }

    internal fun CancellableContinuation<*>.disposeOnCancel(disposableHandle: DisposableHandle) {
        invokeOnCompletion { if (isCancelled) disposableHandle.dispose() }
    }

    internal var SelectionKey.subject: Selectable?
        get() = attachment() as? Selectable
        set(newValue) {
            attach(newValue)
        }

}