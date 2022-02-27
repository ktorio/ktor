// ktlint-disable filename
package io.ktor.network.selector

import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

internal open class SelectableBase(override val channel: SelectableChannel) : Selectable {
    private val _isClosed = AtomicBoolean(false)

    override val suspensions = InterestSuspensionsMap()

    override val isClosed: Boolean
        get() = _isClosed.get()

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
        if (!_isClosed.compareAndSet(false, true)) return

        interestedOps = 0
        suspensions.invokeForEachPresent {
            resumeWithException(ClosedChannelCancellationException())
        }
    }

    override fun dispose() {
        close()
    }

    public companion object {
        val InterestedOps =
            AtomicIntegerFieldUpdater.newUpdater(SelectableBase::class.java, SelectableBase::interestedOps.name)!!
    }
}
