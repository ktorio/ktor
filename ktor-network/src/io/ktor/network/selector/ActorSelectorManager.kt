package io.ktor.network.selector

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import java.nio.channels.*
import java.util.concurrent.atomic.*

class ActorSelectorManager(dispatcher: CoroutineDispatcher) : SelectorManagerSupport(), Closeable {
    @Volatile
    private var selectorRef: Selector? = null

    private val wakeup = AtomicLong()

    @Volatile
    private var inSelect = false

    private val mb = actor<Selectable>(dispatcher, capacity = kotlinx.coroutines.experimental.channels.Channel.UNLIMITED) {
        provider.openSelector()!!.use { selector ->
            selectorRef = selector
            try {
                process(channel, selector)
            } catch (t: Throwable) {
                channel.close()
                cancelAllSuspensions(selector, t)
            } finally {
                channel.close()
                cancelAllSuspensions(selector, null)
                selectorRef = null
            }

            channel.consumeEach {
                cancelAllSuspensions(it, ClosedSendChannelException("Failed to apply interest: selector closed"))
            }
        }
    }

    private suspend fun process(mb: ReceiveChannel<Selectable>, selector: Selector) {
        while (!mb.isClosedForReceive) {
            processInterests(mb, selector)

            if (pending > 0) {
                if (select(selector) > 0) {
                    handleSelectedKeys(selector.selectedKeys(), selector.keys())
                } else {
                    val received = mb.poll()
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

    private fun processInterests(mb: ReceiveChannel<Selectable>, selector: Selector) {
        while (true) {
            val selectable = mb.poll() ?: break
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
            mb.offer(selectable)
            selectWakeup()
        } catch (t: Throwable) {
            cancelAllSuspensions(selectable, t)
        }
    }

    override fun close() {
        mb.close()
        selectWakeup()
    }
}