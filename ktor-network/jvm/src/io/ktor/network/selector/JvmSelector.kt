// ktlint-disable filename
package io.ktor.network.selector

import kotlinx.coroutines.*
import java.io.*
import java.nio.channels.*

public actual interface Selectable : Closeable, DisposableHandle {
    /**
     * Current selectable suspensions map
     */
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
