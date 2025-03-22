/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.*
import java.io.*
import java.nio.channels.*

public actual interface Selectable : Closeable, DisposableHandle {
    /**
     * Current selectable suspensions map
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.Selectable.suspensions)
     */
    public val suspensions: InterestSuspensionsMap

    /**
     * Indicated if the selectable is closed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.Selectable.isClosed)
     */
    public val isClosed: Boolean

    /**
     * current interests
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.Selectable.interestedOps)
     */
    public val interestedOps: Int

    public val channel: SelectableChannel

    /**
     * Apply [state] flag of [interest] to [interestedOps]. Notice that is doesn't actually change selection key.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.Selectable.interestOp)
     */
    public fun interestOp(interest: SelectInterest, state: Boolean)
}
