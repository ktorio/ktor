package io.ktor.network.selector

import kotlinx.coroutines.*
import java.io.*
import java.nio.channels.*
import java.util.concurrent.atomic.*

/**
 * A selectable entity with selectable NIO [channel], [interestedOps] subscriptions
 */
interface Selectable : Closeable, DisposableHandle {

    val suspensions: InterestSuspensionsMap

    /**
     * associated channel
     */
    val channel: SelectableChannel

    /**
     * current interests
     */
    val interestedOps: Int

    /**
     * Apply [state] flag of [interest] to [interestedOps]. Notice that is doesn't actually change selection key.
     */
    fun interestOp(interest: SelectInterest, state: Boolean)
}

internal open class SelectableBase(override val channel: SelectableChannel) : Selectable {
    override val suspensions = InterestSuspensionsMap()

    @Volatile
    override var interestedOps: Int = 0

    override fun interestOp(interest: SelectInterest, state: Boolean) {
        val flag = interest.flag

        while (true) {
            val before = interestedOps
            val after = if (state) before or flag else before and flag.inv()
            if (InterestedOps.compareAndSet(this, before, after)) break
        }
    }

    override fun close() {
        interestedOps = 0
        suspensions.invokeForEachPresent {
            tryResumeWithException(ClosedChannelException())?.let { token ->
                completeResume(token)
            }
        }
    }

    override fun dispose() {
        close()
    }

    companion object {
        val InterestedOps = AtomicIntegerFieldUpdater.newUpdater(SelectableBase::class.java, SelectableBase::interestedOps.name)!!
    }
}

