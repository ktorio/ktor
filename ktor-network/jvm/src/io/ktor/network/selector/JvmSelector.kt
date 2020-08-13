package io.ktor.network.selector

import io.ktor.util.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.channels.*

@InternalAPI
public actual interface Selectable : Closeable, DisposableHandle {
    /**
     * Current selectable suspensions map
     */
    @InternalAPI
    public val suspensions: InterestSuspensionsMap

    /**
     * current interests
     */
    public val interestedOps: Int

    /**
     * Apply [state] flag of [interest] to [interestedOps]. Notice that is doesn't actually change selection key.
     */
    public fun interestOp(interest: SelectInterest, state: Boolean)

    public val channel: SelectableChannel
}
