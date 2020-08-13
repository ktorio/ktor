package io.ktor.network.selector

import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

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
            resumeWithException(ClosedChannelCancellationException())
        }
    }

    override fun dispose() {
        close()
    }

    companion object {
        val InterestedOps =
            AtomicIntegerFieldUpdater.newUpdater(SelectableBase::class.java, SelectableBase::interestedOps.name)!!
    }
}
