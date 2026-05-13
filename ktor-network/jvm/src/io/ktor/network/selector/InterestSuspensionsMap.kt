/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.*
import java.util.concurrent.atomic.*

/**
 * A thread-safe map that manages suspensions for different [SelectInterest] types.
 *
 * This class is used internally by the selector to track coroutine continuations
 * waiting for specific I/O operations (read, write, accept, connect) to become ready.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap)
 */
public class InterestSuspensionsMap {
    @Volatile
    private var readHandlerReference: CancellableContinuation<Unit>? = null

    @Volatile
    private var writeHandlerReference: CancellableContinuation<Unit>? = null

    @Volatile
    private var connectHandlerReference: CancellableContinuation<Unit>? = null

    @Volatile
    private var acceptHandlerReference: CancellableContinuation<Unit>? = null

    /**
     * Registers a suspension [continuation] for the specified [interest].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.addSuspension)
     *
     * @param interest the type of I/O operation to wait for
     * @param continuation the coroutine continuation to resume when the operation is ready
     * @throws IllegalStateException if a handler for this interest is already registered
     */
    public fun addSuspension(interest: SelectInterest, continuation: CancellableContinuation<Unit>) {
        val updater = updater(interest)

        if (!updater.compareAndSet(this, null, continuation)) {
            error("Handler for ${interest.name} is already registered")
        }
    }

    /**
     * Invokes the given [block] for each registered suspension whose interest matches the [readyOps] flags.
     * The matching suspensions are removed from the map before invoking the block.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.invokeForEachPresent)
     *
     * @param readyOps a bitmask of ready operations (from NIO selection key)
     * @param block the action to perform on each matching continuation
     */
    public inline fun invokeForEachPresent(readyOps: Int, block: CancellableContinuation<Unit>.() -> Unit) {
        val flags = SelectInterest.flags

        for (ordinal in flags.indices) {
            if (flags[ordinal] and readyOps != 0) {
                removeSuspension(ordinal)?.block()
            }
        }
    }

    /**
     * Invokes the given [block] for each registered suspension, regardless of ready state.
     * The suspensions are removed from the map before invoking the block.
     * This is typically used for cleanup or cancellation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.invokeForEachPresent)
     *
     * @param block the action to perform on each continuation with its associated interest
     */
    public inline fun invokeForEachPresent(block: CancellableContinuation<Unit>.(SelectInterest) -> Unit) {
        for (interest in SelectInterest.AllInterests) {
            removeSuspension(interest)?.run { block(interest) }
        }
    }

    /**
     * Removes and returns the suspension registered for the specified [interest].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.removeSuspension)
     *
     * @param interest the type of I/O operation
     * @return the removed continuation, or null if no suspension was registered
     */
    public fun removeSuspension(interest: SelectInterest): CancellableContinuation<Unit>? =
        updater(interest).getAndSet(this, null)

    /**
     * Removes and returns the suspension registered for the interest at the given ordinal index.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.removeSuspension)
     *
     * @param interestOrdinal the ordinal index of the [SelectInterest]
     * @return the removed continuation, or null if no suspension was registered
     */
    public fun removeSuspension(interestOrdinal: Int): CancellableContinuation<Unit>? =
        updaters[interestOrdinal].getAndSet(this, null)

    override fun toString(): String {
        return "R $readHandlerReference W $writeHandlerReference C $connectHandlerReference A $acceptHandlerReference"
    }

    public companion object {
        @Suppress("UNCHECKED_CAST")
        private val updaters = SelectInterest.AllInterests.map { interest ->
            val property = when (interest) {
                SelectInterest.READ -> InterestSuspensionsMap::readHandlerReference
                SelectInterest.WRITE -> InterestSuspensionsMap::writeHandlerReference
                SelectInterest.ACCEPT -> InterestSuspensionsMap::acceptHandlerReference
                SelectInterest.CONNECT -> InterestSuspensionsMap::connectHandlerReference
            }
            AtomicReferenceFieldUpdater.newUpdater(
                InterestSuspensionsMap::class.java,
                CancellableContinuation::class.java,
                property.name
            ) as AtomicReferenceFieldUpdater<InterestSuspensionsMap, CancellableContinuation<Unit>?>
        }.toTypedArray()

        private fun updater(
            interest: SelectInterest
        ): AtomicReferenceFieldUpdater<InterestSuspensionsMap, CancellableContinuation<Unit>?> =
            updaters[interest.ordinal]
    }
}
